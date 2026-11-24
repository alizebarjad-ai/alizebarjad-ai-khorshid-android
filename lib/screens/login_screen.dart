import 'package:flutter/material.dart';
import '../services/api_client.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});
  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final api = ApiClient();
  final username = TextEditingController();
  final password = TextEditingController();
  bool busy = false;
  String error = '';

  @override
  void dispose() {
    username.dispose();
    password.dispose();
    super.dispose();
  }

  Future<void> submit() async {
    if (username.text.trim().isEmpty || password.text.isEmpty) return;
    setState(() { busy = true; error = ''; });
    try {
      final result = await api.login(username.text, password.text);
      if (result['ok'] != true) throw Exception('ورود انجام نشد.');
      final session = await api.session();
      if (session['authenticated'] != true) throw Exception('نشست تأیید نشد.');
      if (!mounted) return;
      Navigator.of(context).pushReplacement(MaterialPageRoute(
        builder: (_) => MemberHome(api: api),
      ));
    } catch (_) {
      if (mounted) setState(() => error = 'ورود ناموفق بود. اطلاعات ورود یا اتصال را بررسی کنید.');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(child: Center(child: SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 440), child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Icon(Icons.wb_sunny_outlined, size: 64, color: Color(0xFFC7A45B)),
          const SizedBox(height: 18),
          const Text('خورشید مفاخر', textAlign: TextAlign.center,
            style: TextStyle(fontSize: 26, fontWeight: FontWeight.bold, color: Color(0xFF14243A))),
          const SizedBox(height: 8),
          const Text('ورود به پنل اعضا', textAlign: TextAlign.center),
          const SizedBox(height: 28),
          TextField(controller: username, decoration: const InputDecoration(labelText: 'نام کاربری یا ایمیل')),
          const SizedBox(height: 12),
          TextField(controller: password, obscureText: true, onSubmitted: (_) => submit(),
            decoration: const InputDecoration(labelText: 'گذرواژه')),
          if (error.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 12),
            child: Text(error, style: const TextStyle(color: Colors.red))),
          const SizedBox(height: 20),
          FilledButton(onPressed: busy ? null : submit,
            child: busy ? const CircularProgressIndicator() : const Text('ورود')),
        ],
      )),
    ))),
  );
}

class MemberHome extends StatelessWidget {
  const MemberHome({super.key, required this.api});
  final ApiClient api;

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('خورشید مفاخر'), actions: [
      IconButton(icon: const Icon(Icons.logout), onPressed: () async {
        await api.logout();
        if (context.mounted) Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute(builder: (_) => const LoginScreen()), (_) => false);
      }),
    ]),
    body: FutureBuilder<Map<String, dynamic>>(
      future: api.dio.get('/api/workspace/member').then((r) => Map<String, dynamic>.from(r.data as Map)),
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError || !snapshot.hasData) {
          return const Center(child: Text('دریافت اطلاعات عضو انجام نشد.'));
        }
        final member = snapshot.data!['member'];
        final name = member is Map ? member['fullName'] ?? 'عضو گرامی' : 'عضو گرامی';
        return Center(child: Padding(padding: const EdgeInsets.all(24),
          child: Text('درود، $name', style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold))));
      },
    ),
  );
}
