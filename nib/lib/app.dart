import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/theme/nib_theme.dart';
import 'features/all/entry_list_page.dart';
import 'features/calendar/calendar_page.dart';
import 'features/editor/quick_capture_page.dart';
import 'features/me/me_page.dart';
import 'features/today/today_page.dart';
import 'providers.dart';

class NibApp extends ConsumerWidget {
  const NibApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final config = ref.watch(themeConfigProvider);
    return MaterialApp(
      title: '落笔',
      debugShowCheckedModeBanner: false,
      theme: nibTheme(config),
      home: const NibShell(),
    );
  }
}

class NibShell extends ConsumerStatefulWidget {
  const NibShell({super.key});

  @override
  ConsumerState<NibShell> createState() => _NibShellState();
}

class _NibShellState extends ConsumerState<NibShell> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(
        index: _index,
        children: const [
          TodayPage(),
          AllPage(),
          CalendarPage(),
          MePage(),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (index) => setState(() => _index = index),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.today_outlined),
            selectedIcon: Icon(Icons.today),
            label: '今日',
          ),
          NavigationDestination(
            icon: Icon(Icons.list_alt_outlined),
            selectedIcon: Icon(Icons.list_alt),
            label: '全部',
          ),
          NavigationDestination(
            icon: Icon(Icons.calendar_month_outlined),
            selectedIcon: Icon(Icons.calendar_month),
            label: '日历',
          ),
          NavigationDestination(
            icon: Icon(Icons.person_outline),
            selectedIcon: Icon(Icons.person),
            label: '我的',
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => Navigator.of(context).push(
          MaterialPageRoute<void>(builder: (_) => const QuickCapturePage()),
        ),
        child: const Icon(Icons.add),
      ),
    );
  }
}
