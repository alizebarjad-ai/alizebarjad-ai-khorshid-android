# اپلیکیشن اندروید خورشید مفاخر

نسخه اول اپلیکیشن Native اعضا با Flutter.

## وضعیت
این مخزن تازه ایجاد شده و کد اولیه در حال انتقال است. این نسخه هنوز Build یا روی دستگاه واقعی آزمایش نشده و برای انتشار فروشگاهی آماده نیست.

## راه‌اندازی
```bash
flutter create . --platforms=android --org ir.khorshidmafakher
flutter pub get
flutter analyze
flutter run
```

## دامنه سرویس
https://portal.khorshid-mafakher.ir

## ملاحظات
- از APIهای موجود پورتال استفاده می‌شود.
- رمز عبور و کوکی نشست نباید در لاگ یا مخزن ثبت شوند.
- احراز هویت فعلی مبتنی بر کوکی HttpOnly است.
- پیش از انتشار، رفتار Set-Cookie و کنترل Same-Origin باید روی Android واقعی تست شود.
- Push، پیوست فایل و تمدید نشست موبایل در این مرحله تکمیل نشده‌اند.
