import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class ApiClient {
  static const baseUrl = 'https://portal.khorshid-mafakher.ir';
  static const _cookieKey = 'khorshid_session_cookie';
  final _secure = const FlutterSecureStorage();
  late final Dio dio;

  ApiClient() {
    dio = Dio(BaseOptions(
      baseUrl: baseUrl,
      connectTimeout: const Duration(seconds: 15),
      receiveTimeout: const Duration(seconds: 20),
      headers: {'Accept': 'application/json', 'Origin': baseUrl},
    ));
    dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        final cookie = await _secure.read(key: _cookieKey);
        if (cookie != null && cookie.isNotEmpty) {
          options.headers['Cookie'] = cookie;
        }
        handler.next(options);
      },
      onResponse: (response, handler) async {
        final cookies = response.headers.map['set-cookie'] ?? const <String>[];
        for (final item in cookies) {
          final pair = item.split(';').first.trim();
          if (pair.startsWith('khorshid_session=')) {
            await _secure.write(key: _cookieKey, value: pair);
          }
        }
        handler.next(response);
      },
    ));
  }

  Future<Map<String, dynamic>> login(String username, String password) async {
    final response = await dio.post('/api/auth/login', data: {
      'username': username.trim(),
      'password': password,
    });
    return Map<String, dynamic>.from(response.data as Map);
  }

  Future<Map<String, dynamic>> session() async {
    final response = await dio.get('/api/auth/session');
    return Map<String, dynamic>.from(response.data as Map);
  }

  Future<void> logout() async {
    try {
      await dio.post('/api/auth/logout', data: <String, dynamic>{});
    } finally {
      await _secure.delete(key: _cookieKey);
    }
  }
}
