import 'package:flutter/material.dart';

String twoDigits(int value) => value.toString().padLeft(2, '0');

String dateKey(DateTime value) =>
    '${value.year.toString().padLeft(4, '0')}-${twoDigits(value.month)}-${twoDigits(value.day)}';

String timeKey(TimeOfDay value) => '${twoDigits(value.hour)}:${twoDigits(value.minute)}';

DateTime addDays(DateTime value, int days) =>
    DateTime(value.year, value.month, value.day + days);

DateTime? parseDateKey(String? value) {
  if (value == null || value.isEmpty) return null;
  final parts = value.split('-');
  if (parts.length != 3) return null;
  final year = int.tryParse(parts[0]);
  final month = int.tryParse(parts[1]);
  final day = int.tryParse(parts[2]);
  if (year == null || month == null || day == null) return null;
  return DateTime(year, month, day);
}

TimeOfDay? parseTimeKey(String? value) {
  if (value == null || value.isEmpty) return null;
  final parts = value.split(':');
  if (parts.length != 2) return null;
  final hour = int.tryParse(parts[0]);
  final minute = int.tryParse(parts[1]);
  if (hour == null || minute == null) return null;
  return TimeOfDay(hour: hour, minute: minute);
}
