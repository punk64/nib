import 'package:flutter/material.dart';

@immutable
class NibTokens extends ThemeExtension<NibTokens> {
  const NibTokens({
    required this.bg,
    required this.card,
    required this.line,
    required this.text,
    required this.textMuted,
    required this.accent,
  });

  final Color bg;
  final Color card;
  final Color line;
  final Color text;
  final Color textMuted;
  final Color accent;

  @override
  NibTokens copyWith({
    Color? bg,
    Color? card,
    Color? line,
    Color? text,
    Color? textMuted,
    Color? accent,
  }) =>
      NibTokens(
        bg: bg ?? this.bg,
        card: card ?? this.card,
        line: line ?? this.line,
        text: text ?? this.text,
        textMuted: textMuted ?? this.textMuted,
        accent: accent ?? this.accent,
      );

  @override
  NibTokens lerp(ThemeExtension<NibTokens>? other, double t) {
    if (other is! NibTokens) return this;
    return NibTokens(
      bg: Color.lerp(bg, other.bg, t)!,
      card: Color.lerp(card, other.card, t)!,
      line: Color.lerp(line, other.line, t)!,
      text: Color.lerp(text, other.text, t)!,
      textMuted: Color.lerp(textMuted, other.textMuted, t)!,
      accent: Color.lerp(accent, other.accent, t)!,
    );
  }
}

@immutable
class ThemeConfig {
  const ThemeConfig({
    this.styleId = 'original',
    this.paletteId = 'indigo',
    this.brightness = Brightness.dark,
  });

  final String styleId;
  final String paletteId;
  final Brightness brightness;

  ThemeConfig copyWith({
    String? styleId,
    String? paletteId,
    Brightness? brightness,
  }) =>
      ThemeConfig(
        styleId: styleId ?? this.styleId,
        paletteId: paletteId ?? this.paletteId,
        brightness: brightness ?? this.brightness,
      );
}

const darkTokens = NibTokens(
  bg: Color(0xFF121317),
  card: Color(0xFF1B1D23),
  line: Color(0xFF2C2F38),
  text: Color(0xFFECEEF4),
  textMuted: Color(0xFF9AA0AE),
  accent: Color(0xFF8B9BFF),
);

const lightTokens = NibTokens(
  bg: Color(0xFFF6F7FA),
  card: Color(0xFFFFFFFF),
  line: Color(0xFFE2E5EC),
  text: Color(0xFF1C1E24),
  textMuted: Color(0xFF6B7180),
  accent: Color(0xFF3F51B5),
);

const _paletteSeeds = {
  'indigo': Color(0xFF6C7CF5),
  'amber': Color(0xFFE8A33D),
  'teal': Color(0xFF3FBFA8),
};

ThemeData nibTheme(ThemeConfig config) {
  final seed = _paletteSeeds[config.paletteId] ?? _paletteSeeds['indigo']!;
  return ThemeData(
    useMaterial3: true,
    brightness: config.brightness,
    colorScheme: ColorScheme.fromSeed(
      seedColor: seed,
      brightness: config.brightness,
    ),
    extensions: <ThemeExtension<dynamic>>[
      config.brightness == Brightness.dark ? darkTokens : lightTokens,
    ],
  );
}
