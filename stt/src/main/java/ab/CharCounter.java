package ab;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

public class CharCounter {
  private Map<Character, AtomicInteger> map = new HashMap<>();
  public ToIntFunction<String> weightFunction = s -> s.length() > 2 ? 1 : 100;

  public void addValue(String s) {
    int weight = weightFunction == null ? 1 : weightFunction.applyAsInt(s);
    s.chars().mapToObj(c -> (char) c)
        .forEach(c -> map.computeIfAbsent(c, k -> new AtomicInteger()).getAndAdd(weight));
  }

  public static final char[] DOTS_09 = new char[]{'.', '-', '-', '+', '+', '*', '*', '#', '#', '%'};
  public static final char[] DOTS_16 = new char[]{'0', '.', '-', '+', '*', '#', '%'};

  public String getValues() {
    Map<Integer, Character> dots09 = new HashMap<>();
    List<Integer> valuesList = map.values().stream().map(AtomicInteger::get).sorted().collect(Collectors.toList());
    for (int i = 0, size = valuesList.size(); i < size; i++) {
      dots09.put(valuesList.get(i), DOTS_09[i * 10 / size]);
    }
    StringBuilder s1 = new StringBuilder(), s2 = new StringBuilder(), s3 = new StringBuilder();
    map.keySet().stream().filter(c -> c != ' ').sorted().forEach(c -> {
      s1.append(c);
      s2.append(DOTS_16[Integer.toString(map.get(c).get()).length()]);
      s3.append(dots09.get(map.get(c).get()));
    });
    return '\n' + s1.toString() + '\n' + s3.toString();
  }

}
