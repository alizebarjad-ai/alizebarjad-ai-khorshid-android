# ممیزی قرارداد API پورتال برای اپ اندروید

تاریخ بررسی: ۱۴۰۵/۰۹/۰۳
مبنای بررسی: سورس شاخه `main` مخزن `alizebarjad-ai/khorshid-portal-v2`

این مستند از روی درخت کامل Git و فایل‌های واقعی پورتال استخراج شده است. هیچ تغییری در مخزن پورتال یا دیتابیس عملیاتی اعمال نشده است.

## ۱. احراز هویت و نشست

### ورود
- `POST /api/auth/login`
- بدنه JSON strict:
  - `username`: رشته، پس از trim، حداکثر ۱۰۰ نویسه
  - `password`: رشته، حداکثر ۱۰۲۴ نویسه
- پاسخ موفق: `{ "ok": true }`
- خطاهای ممکن: 400، 401، 403، 429 و 503
- هدر `Origin` اجباری است و باید دقیقاً با host و پروتکل درخواست منطبق باشد. در تولید HTTPS الزامی است.
- ورود با نام کاربری یا ایمیل پذیرفته می‌شود.

### نشست
- کوکی: `khorshid_session`
- ویژگی‌ها: HttpOnly، Secure در تولید، SameSite=Lax، Path=/
- اعتبار نشست: ۸ ساعت
- توکن تصادفی ۳۲ بایتی است و فقط SHA-256 آن در دیتابیس ذخیره می‌شود.
- `GET /api/auth/session`: پاسخ موفق شامل username است؛ نقش و مشخصات کامل عضو را برنمی‌گرداند.
- اعتبار نشست با انقضا، revoke، غیرفعال شدن حساب یا تغییر credentialVersion از بین می‌رود.
- `POST /api/auth/logout`: نیازمند Origin معتبر است و نشست سمت سرور را revoke می‌کند.

## ۲. API واقعی داشبورد عضو

مسیر `/api/workspace/member` واقعاً وجود دارد؛ این مسیر از route داینامیک `src/app/api/workspace/[department]/route.ts` پاسخ می‌گیرد. اشاره قبلی به نبود این مسیر اشتباه بود و در این نسخه اصلاح شده است.

### دریافت داشبورد
- `GET /api/workspace/member`
- سرور نشست را بررسی می‌کند، نقش را از assignmentهای ذخیره‌شده در دیتابیس به دست می‌آورد و دسترسی به workspace عضو را با نقش MEMBER کنترل می‌کند.
- خروجی اصلی شامل:
  - `member`: مشخصات عضو، وضعیت عضویت، امتیاز، باشگاه‌ها، درخواست‌های عنوان حرفه‌ای و محتوای ارسالی
  - `notices`: اطلاعیه‌های فعال و متناسب با نوع عضویت
  - `advertisements`: فرصت‌ها و آگهی‌های فعال
  - `notifications`: اعلان‌های شخصی عضو
  - `programs`: برنامه‌ها و رویدادهای قابل مشاهده
  - `availableClubs`: باشگاه‌های فعال
  - `projectProposals`: سوابق قدیمی ثبت‌شده در ExternalIntake
- پاسخ شامل `ok: true` و `department: "member"` نیز هست.

### خلاصه‌های داشبورد
- `GET /api/workspace/member?summary=profile`: خلاصه پرونده و درخواست‌های عنوان عضو.
- `GET /api/workspace/member?summary=notifications`: تعداد اعلان‌های خوانده‌نشده.
- `GET /api/workspace/member?summary=conversations`: فهرست مکاتبات و پیام‌های هر مکاتبه؛ دریافت این خلاصه، پیام‌های پاسخ‌داده‌شده توسط کارکنان را به‌عنوان خوانده‌شده ثبت می‌کند.
- `GET /api/workspace/member?summary=badge`: تعداد پیام‌های خوانده‌نشده.

## ۳. پیام‌ها و مکاتبات

همه عملیات عادی عضو از طریق `/api/workspace/member` انجام می‌شود.

### ایجاد مکاتبه جدید
`POST /api/workspace/member`

```json
{
  "action": "SEND_DEPARTMENT_MESSAGE",
  "recipientRole": "MEMBERS_DEPUTY",
  "body": "متن پیام"
}
```

مقادیر مجاز recipientRole:
- MEMBERS_DEPUTY
- ARTS_DEPUTY
- CSR_DEPUTY
- EDUCATION_DEPUTY
- ECONOMIC_DEPUTY
- MEDIA_DEPUTY
- OWNER

متن باید ۳ تا ۵۰۰۰ نویسه باشد. ایجاد مکاتبه، پیام اولیه، اعلان کارکنان، اعلان سابقه ارسالی عضو و رویداد حسابرسی در یک تراکنش ثبت می‌شوند. اگر برای معاونت مقصد حساب فعال وجود نداشته باشد، پاسخ 409 با RECIPIENT_UNAVAILABLE است.

### پاسخ به مکاتبه
```json
{
  "action": "REPLY_MEMBER_CONVERSATION",
  "conversationId": "شناسه مکاتبه",
  "body": "متن پاسخ"
}
```

متن پاسخ ۲ تا ۵۰۰۰ نویسه است. سرور مالکیت مکاتبه را با member.accountId کنترل می‌کند و مکاتبه بسته‌شده قابل پاسخ نیست.

### خواندن اعلان
```json
{
  "action": "MARK_NOTIFICATION_READ",
  "id": "شناسه اعلان"
}
```

## ۴. پیشنهاد پروژه

برای گردش کار اصلی پروژه‌ها باید از API اختصاصی پروژه استفاده شود، نه action قدیمی `CREATE_MEMBER_PROJECT_PROPOSAL` در workspace عضو. مسیر جدید پرونده واقعی CaseFile با workflow ایجاد می‌کند.

### فهرست پیشنهادهای خود عضو
- `GET /api/projects`
- نقش MEMBER فقط پروژه‌هایی را می‌بیند که پیشنهاددهنده آن بوده یا عضو فعال تیم آن است.
- خروجی: `{ ok: true, projects: [...] }`
- اطلاعات پرونده شامل کد، عنوان، مرحله گردش کار، وضعیت، شرح، هدف، رویدادها و پیام‌های مجاز است.

### ثبت پیشنهاد جدید
- `POST /api/projects`
- هدر Origin معتبر اجباری است.
- بدنه:
```json
{
  "action": "SUBMIT_PROPOSAL",
  "title": "عنوان طرح",
  "objective": "هدف اصلی با حداقل ۱۰ نویسه",
  "description": "شرح کامل با حداقل ۲۰ نویسه",
  "impact": "اثر مورد انتظار با حداقل ۱۰ نویسه",
  "audience": "جامعه هدف",
  "location": "محدوده اجرا",
  "timeline": "زمان‌بندی",
  "budget": "بودجه برآوردی"
}
```
- برای نقش MEMBER حوزه به‌صورت سمت سرور روی SOCIAL_CSR تنظیم می‌شود و پیشنهاد به معاونت اجتماعی و مسئولیت اجتماعی (CSR) می‌رود؛ اپ نباید حوزه یا نقش را از کاربر بگیرد.
- پاسخ موفق HTTP 201 و شامل شناسه، کد و مرحله پرونده است.
- API ثبت قدیمی `CREATE_MEMBER_PROJECT_PROPOSAL` در workspace عضو به‌جای CaseFile، یک ExternalIntake قدیمی ایجاد می‌کند؛ اپ باید از `/api/projects` استفاده کند تا با گردش کار فعلی سایت یکپارچه بماند.

## ۵. امنیت درخواست‌ها

- درخواست‌های POST در workspace با `isSameOriginRequest` محافظت می‌شوند.
- API پروژه نیز Origin را بررسی می‌کند.
- اپ Native باید Origin برابر `https://portal.khorshid-mafakher.ir` را روی درخواست‌های لازم بفرستد؛ این کار با بررسی host و HTTPS پورتال سازگار است.
- نقش MEMBER، شناسه حساب و مالکیت داده باید فقط از نشست معتبر سمت سرور استخراج شوند. اپ نباید role یا memberId را به‌عنوان مرجع مجوز ارسال کند.
- کوکی نشست باید در ذخیره‌سازی رمزگذاری‌شده دستگاه نگهداری شود؛ رمز عبور نباید ذخیره شود.

## ۶. نقشه اتصال اپ نسخه اول

| قابلیت | API |
|---|---|
| ورود | POST /api/auth/login |
| اعتبارسنجی نشست | GET /api/auth/session |
| داشبورد، اطلاعیه‌ها و پروفایل | GET /api/workspace/member |
| خلاصه پیام‌ها | GET /api/workspace/member?summary=conversations |
| ارسال پیام جدید | POST /api/workspace/member با SEND_DEPARTMENT_MESSAGE |
| پاسخ پیام | POST /api/workspace/member با REPLY_MEMBER_CONVERSATION |
| خواندن اعلان | POST /api/workspace/member با MARK_NOTIFICATION_READ |
| فهرست پروژه‌ها | GET /api/projects |
| ثبت پروژه | POST /api/projects با SUBMIT_PROPOSAL |
| خروج | POST /api/auth/logout |

## ۷. موارد خارج از این مرحله

- Push notification هنوز API یا ارائه‌دهنده ثبت‌شده‌ای در این قرارداد ندارد و باید جداگانه طراحی شود.
- Build اندروید، تست روی دستگاه واقعی و آزمون امنیتی انتشار هنوز اجرا نشده‌اند.
- این بررسی و مستندسازی هیچ تغییری در پورتال عملیاتی، دیتابیس یا حساب کاربران ایجاد نکرده است.
