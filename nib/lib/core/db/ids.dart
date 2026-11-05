import 'dart:math';

const _alphabet = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
final _random = Random.secure();

final localDeviceId = newUlid();

String newUlid() {
  final now = DateTime.now().millisecondsSinceEpoch;
  final bytes = List<int>.generate(16, (_) => _random.nextInt(256));
  bytes[0] = (now >> 40) & 255;
  bytes[1] = (now >> 32) & 255;
  bytes[2] = (now >> 24) & 255;
  bytes[3] = (now >> 16) & 255;
  bytes[4] = (now >> 8) & 255;
  bytes[5] = now & 255;
  final buffer = StringBuffer();
  var value = 0;
  var bits = 2;
  for (final b in bytes) {
    value = (value << 8) | b;
    bits += 8;
    while (bits >= 5) {
      bits -= 5;
      buffer.write(_alphabet[(value >> bits) & 31]);
    }
  }
  if (bits > 0) buffer.write(_alphabet[(value << (5 - bits)) & 31]);
  return buffer.toString();
}

int _hlcMs = 0;
int _hlcCounter = 0;

String nextHlc() {
  final now = DateTime.now().millisecondsSinceEpoch;
  if (now > _hlcMs) {
    _hlcMs = now;
    _hlcCounter = 0;
  } else {
    _hlcCounter++;
  }
  return '${_hlcMs.toString().padLeft(16, '0')}-${_hlcCounter.toString().padLeft(4, '0')}-$localDeviceId';
}

String newSortKey() =>
    '${DateTime.now().millisecondsSinceEpoch.toString().padLeft(16, '0')}-${newUlid()}';
