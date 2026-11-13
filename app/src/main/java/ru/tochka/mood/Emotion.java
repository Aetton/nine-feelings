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
      new Emotion("d271572e-eb58-470b-b38d-62cfe79730f4", "Интерес", "👀", COLORS[1]),
      new Emotion("ae8ba4f7-64f2-456f-87f6-0723b4f3d025", "Спокойствие", "😌", COLORS[2]),
      new Emotion("7799a231-d3ce-47a5-ae9c-6ca21bfe17c2", "Злость", "👿", COLORS[3]),
      new Emotion("NEUTRAL", "Нейтрально", "○", COLORS[4]),
      new Emotion("ada4ad86-7d39-4b87-a115-71fe74086c2b", "Грусть", "🙁", COLORS[5]),
      new Emotion("597a6fdc-2068-48c1-b017-f12a0b743fe8", "Нервяк", "😬", COLORS[6]),
      new Emotion("982c4689-3d87-4d06-8abd-36b11b3011fd", "Влюблённость", "😍", COLORS[7]),
      new Emotion("ac871123-31cd-46ec-8a1c-859d55514fa6", "Воодушевление", "🔥", COLORS[8])
    };
  }

  // Version-one entries stored these keys without a separate emotion catalog.
  static Emotion[] legacyDefaults() {
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
