package ab;

import com.ibm.icu.text.RuleBasedNumberFormat;
import lombok.AllArgsConstructor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RawTextProcess implements Runnable {

  private static final Logger log = Logger.getLogger(RawTextProcess.class.getName());

  public static final Pattern RAW_TEXT_PATTERN = Pattern.compile("(\\d)\\s(\\d+)\\.(\\d{3})\\s(\\d+)\\.(\\d{3})\\s(.*)");
  public static final Pattern DIGITS_PATTERN = Pattern.compile(".*[0123456789].*");
  public static final Pattern DIGITS_PERIOD_COMMA = Pattern.compile("[0123456789,\\.]+");
  public static final DateTimeFormatter SRT_TIMECODE = DateTimeFormatter.ofPattern("HH:mm:ss,SSS");

  RuleBasedNumberFormat ruleBasedNumberFormat;
  public String fromNumber(String s) {
    if (DIGITS_PERIOD_COMMA.matcher(s).matches()) s = s.replace(",", "");
    if (s.startsWith("$")) try {
      return ruleBasedNumberFormat.format(Long.parseLong(s.substring(1))) + " dollars";
    } catch (NumberFormatException ignored) {}
    if (s.endsWith("%")) try {
      return ruleBasedNumberFormat.format(Long.parseLong(s.substring(0, s.length() - 1))) + " percent";
    } catch (NumberFormatException ignored) {}
    if (s.endsWith("e")) try { // FIXME: 2021-08-08 need generic
      return ruleBasedNumberFormat.format(Long.parseLong(s.substring(0, s.length() - 1)));
    } catch (NumberFormatException ignored) {}
    if (s.endsWith("er") || s.endsWith("re")) try { // FIXME: 2021-08-08 need generic
      return ruleBasedNumberFormat.format(Long.parseLong(s.substring(0, s.length() - 2)));
    } catch (NumberFormatException ignored) {}
    try {
      return ruleBasedNumberFormat.format(Long.parseLong(s));
    } catch (NumberFormatException e) {
      throw e; // debug breakpoint
    }
  }

  private List<SrtItem> buildSrt(String fileName) {
    String languageCode = Stt.localePrefix(fileName).orElseThrow();
    Locale locale = Locale.forLanguageTag(languageCode);
    ruleBasedNumberFormat = new RuleBasedNumberFormat(locale, RuleBasedNumberFormat.SPELLOUT);
    List<SrtItem> srt = new ArrayList<>();
    CharCounter charCounter = new CharCounter();
    final List<String> stream;
    try {
      stream = Files.readAllLines(Paths.get(fileName));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    Duration lastTimeStamp = Duration.ZERO;
    for (String line : stream) {
      Matcher m = RAW_TEXT_PATTERN.matcher(line);
      if (!m.matches()) throw new IllegalStateException();
      int speaker = Integer.parseInt(m.group(1));
      Duration d1 = Duration.ofSeconds(Long.parseLong(m.group(2))).plusMillis(Long.parseLong(m.group(3)));
      Duration d2 = Duration.ofSeconds(Long.parseLong(m.group(4))).plusMillis(Long.parseLong(m.group(5)));
      String s = m.group(6);
      if (s.startsWith(" ") || s.endsWith(" ") || d1.compareTo(d2) > 0) { // can be zero duration
        throw new IllegalStateException();
      }
      s = s.toLowerCase(locale);
      if (DIGITS_PATTERN.matcher(s).matches()) s = fromNumber(s); // replace digits
      s = s.replace('.', ' ').replace(',', ' ').trim();
      SrtItem srtItem = new SrtItem(speaker, d1, d2, s);
      charCounter.addValue(s);
      if (!srt.isEmpty()) {
        int lastIndex = srt.size() - 1;
        Optional<SrtItem> joinedItem = srt.get(lastIndex).join(srtItem);
        if (joinedItem.isPresent()) {
          srtItem = joinedItem.get();
          srt.remove(lastIndex);
        }
      }
      if (d2.compareTo(lastTimeStamp) < 0) break;
      lastTimeStamp = d2;
      srt.add(srtItem);
    }
    log.info(fileName + charCounter.getValues());
    return srt;
  }

  @Override
  public void run() {
    for (String fileName : Stt.listFilesExt(".flac.txt")) {
      List<SrtItem> srt = buildSrt(fileName);
      StringBuilder sb = new StringBuilder();
      StringBuilder wt = new StringBuilder();
      for (int i = 0; i < srt.size(); i++) {
        SrtItem item = srt.get(i);
        if (i == 0 || !srt.get(i-1).end.equals(item.start)) wt.append(" -");
        wt.append(' ').append(item.transcript);
        sb.append(i + 1).append('\n')
            .append(SRT_TIMECODE.format(LocalTime.MIN.plus(item.start))).append(" --> ")
            .append(SRT_TIMECODE.format(LocalTime.MIN.plus(item.end))).append('\n')
            .append(item.transcript).append("\n\n");
      }
      try {
        final String fn = fileName.substring(0, fileName.length() - 9);
        Files.writeString(Paths.get(fn + ".srt"), sb.toString());
        Files.writeString(Paths.get(fn + ".wt"), wt.toString());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  public static void main(String[] args) {
    new RawTextProcess().run();
  }

  @AllArgsConstructor
  public static class SrtItem {
    public static final int MAGIC_JOIN_LIMIT = 80;
    private final int speakerTag;
    private final Duration start;
    private final Duration end;
    private final String transcript;
    public Optional<SrtItem> join(SrtItem other) {
      final SrtItem b; // before
      final SrtItem a; // after
      final boolean thisBefore = this.end.equals(other.start);
      final boolean thisAfter = other.end.equals(this.start);
      if (thisAfter && !thisBefore) {
        b = other; a = this;
      } else if (thisBefore && !thisAfter) {
        b = this; a = other;
      } else {
        return Optional.empty();
      }
      return b.transcript.length() + a.transcript.length() > MAGIC_JOIN_LIMIT ? Optional.empty() :
          Optional.of(new SrtItem(b.speakerTag, b.start, a.end, b.transcript + " " + a.transcript));
    }
  }

}
