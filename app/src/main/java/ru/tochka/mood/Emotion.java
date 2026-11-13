package ru.tochka.mood;

public final class Emotion {
  public final String key, title, symbol;
  public final int color;

  public Emotion(String key, String title, String symbol, int color) {
    this.key = key;
    this.title = title;
    this.symbol = symbol;
    this.color = color;
  }

  public static final int[] COLORS = {
    0xFFF0D88A,
    0xFFE3C49E,
    0xFFC5D6B7,
    0xFFE9AAA0,
    0xFFDDD9D1,
    0xFFB8CCDC,
    0xFFD1C0DF,
    0xFFBFCBA8,
    0xFFF0BB98
  };

  public static Emotion[] defaults() {
    return new Emotion[] {
      new Emotion("JOY", "Радость", "☺", COLORS[0]),
          new Emotion("INTEREST", "Интерес", "✧", COLORS[1]),
      new Emotion("CALM", "Спокойствие", "≈", COLORS[2]),
          new Emotion("ANGER", "Злость", "ϟ", COLORS[3]),
      new Emotion("NEUTRAL", "Нейтрально", "○", COLORS[4]),
          new Emotion("SADNESS", "Грусть", "☂", COLORS[5]),
      new Emotion("ANXIETY", "Тревога", "〰", COLORS[6]),
          new Emotion("DISGUST", "Отвращение", "×", COLORS[7]),
      new Emotion("INSPIRED", "Воодушевление", "✺", COLORS[8])
    };
  }
}
