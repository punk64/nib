import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app.dart';
import 'core/db/database.dart';
import 'features/reminder/notification_service.dart';
import 'features/reminder/reminder_bootstrap.dart';
import 'providers.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final db = AppDatabase();
  await db.ensureSeed();
  await initReminders();
  await bootstrapReminders(db);
  runApp(ProviderScope(
    overrides: [dbProvider.overrideWithValue(db)],
    child: const NibApp(),
  ));
}
