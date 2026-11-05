import 'package:android_intent_plus/android_intent.dart';
import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

import 'notification_service.dart';

class PermissionPage extends StatefulWidget {
  const PermissionPage({super.key});

  @override
  State<PermissionPage> createState() => _PermissionPageState();
}

class _PermissionPageState extends State<PermissionPage> {
  bool _notification = false;
  bool _exact = false;
  bool _battery = false;

  @override
  void initState() {
    super.initState();
    _refresh();
  }

  Future<void> _refresh() async {
    final notification = await Permission.notification.isGranted;
    final exact = await canScheduleExact();
    final battery = await Permission.ignoreBatteryOptimizations.isGranted;
    if (!mounted) return;
    setState(() {
      _notification = notification;
      _exact = exact;
      _battery = battery;
    });
  }

  Future<void> _openBatterySettings() async {
    await const AndroidIntent(
      action: 'android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS',
    ).launch();
  }

  Future<void> _openAppSettings() async {
    await openAppSettings();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('提醒权限')),
      body: ListView(
        children: [
          SwitchListTile(
            title: const Text('通知'),
            subtitle: const Text('不开启将收不到任何提醒'),
            value: _notification,
            onChanged: (_) async {
              await Permission.notification.request();
              await _refresh();
            },
          ),
          SwitchListTile(
            title: const Text('精确闹钟'),
            subtitle: const Text('关闭后提醒将延迟最多 15 分钟'),
            value: _exact,
            onChanged: (_) async {
              await requestExactAlarms();
              await _refresh();
            },
          ),
          SwitchListTile(
            title: const Text('忽略电池优化'),
            subtitle: const Text('国内 ROM 上不开容易被杀后台'),
            value: _battery,
            onChanged: (_) async {
              await _openBatterySettings();
              await _refresh();
            },
          ),
          ListTile(
            title: const Text('自启动'),
            subtitle: const Text('部分厂商需在应用详情里手动开启'),
            trailing: const Icon(Icons.open_in_new, size: 18),
            onTap: _openAppSettings,
          ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: FilledButton(
              onPressed: _refresh,
              child: const Text('重新检查'),
            ),
          ),
        ],
      ),
    );
  }
}
