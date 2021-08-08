package ab;

import com.ibm.icu.text.RuleBasedNumberFormat;
import lombok.AllArgsConstructor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class RawTextProcess implements Runnable {

  private static final Logger log = Logger.getLogger(RawTextProcess.class.getName());

  public static final Pattern RAW_TEXT_PATTERN = Pattern.compile("(\\d)\\s(\\d+)\\.(\\d{3})\\s(\\d+)\\.(\\d{3})\\s(.*)");
  public static final Pattern DIGITS_PATTERN = Pattern.compile(".*[0123456789].*");
  public static final Pattern DIGITS_PERIOD_COMMA = Pattern.compile("[0123456789,\\.]+");

  public void countChars(String s, Map<Character, AtomicInteger> counter) {
    AtomicInteger atomicInteger;
    int weight = s.length() > 2 ? 1 : 100;
    s.chars().mapToObj(c -> (char) c).map(c -> counter.computeIfAbsent(c, k -> new AtomicInteger()))
        .forEach(atomicInteger1 -> atomicInteger1.getAndAdd(weight));
  }

  RuleBasedNumberFormat ruleBasedNumberFormat;
  public String fromNumber(String s) {
    if (s.startsWith("$")) try {
      return ruleBasedNumberFormat.format(Long.parseLong(s.substring(1))) + " dollars";
    } catch (NumberFormatException ignored) {}
    if (s.endsWith("%")) try {
      return ruleBasedNumberFormat.format(Long.parseLong(s.substring(0, s.length() - 1))) + " percent";
    } catch (NumberFormatException ignored) {}
    if (s.endsWith("e")) try { // need generic
      return ruleBasedNumberFormat.format(Long.parseLong(s.substring(0, s.length() - 1)));
    } catch (NumberFormatException ignored) {}
    if (s.endsWith("er") || s.endsWith("re")) try { // need generic
      return ruleBasedNumberFormat.format(Long.parseLong(s.substring(0, s.length() - 2)));
    } catch (NumberFormatException ignored) {}
    try {
      return ruleBasedNumberFormat.format(Long.parseLong(s));
    } catch (NumberFormatException e) {
      throw e; // debug breakpoint
    }
  }

  @Override
  public void run() {
    for (String fileName : Stt.listFilesExt(".flac.txt")) {
      String languageCode = Stt.localePrefix(fileName).orElseThrow();
      Locale locale = Locale.forLanguageTag(languageCode);
      ruleBasedNumberFormat = new RuleBasedNumberFormat(locale, RuleBasedNumberFormat.SPELLOUT);
      log.info(fileName + " -> ");
      List<SrtItem> srt = new ArrayList<>();
      Map<Character, AtomicInteger> charCounter = new HashMap<>();
      try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
        stream.forEach(line -> {
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
          countChars(s, charCounter);
          if (!srt.isEmpty()) {
            int lastIndex = srt.size() - 1;
            Optional<SrtItem> joinedItem = srt.get(lastIndex).join(srtItem);
            if (joinedItem.isPresent()) {
              srtItem = joinedItem.get();
              srt.remove(lastIndex);
            }
          }
          srt.add(srtItem);
        });
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      StringBuilder s1 = new StringBuilder();
      StringBuilder s2 = new StringBuilder();
      char[] dots = new char[]{'0', '.', '.', '+', '#', '@', '@'};
      charCounter.remove(' ');
      charCounter.keySet().stream().sorted().forEach(c -> { s1.append(c);
        s2.append(dots[Integer.toString(charCounter.get(c).get()).length()]);});
      System.out.println(s1.toString());
      System.out.println(s2.toString());
      int size = srt.size();
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
