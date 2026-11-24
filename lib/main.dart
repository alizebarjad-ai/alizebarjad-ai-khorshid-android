import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'screens/login_screen.dart';

void main() => runApp(const KhorshidApp());

class KhorshidApp extends StatelessWidget {
  const KhorshidApp({super.key});

  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false,
    title: 'خورشید مفاخر',
    locale: const Locale('fa'),
    supportedLocales: const [Locale('fa'), Locale('en')],
    localizationsDelegates: GlobalMaterialLocalizations.delegates,
    theme: ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(
        seedColor: const Color(0xFF14243A),
        primary: const Color(0xFF14243A),
        secondary: const Color(0xFFC7A45B),
      ),
      scaffoldBackgroundColor: const Color(0xFFF7F5EF),
    ),
    home: const LoginScreen(),
  );
}
