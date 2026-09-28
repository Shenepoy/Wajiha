<!-- markdownlint-disable MD033 MD060 -->

<div dir="rtl" lang="ar">

<p align="center">
  <img src="assets/wajiha-logo.svg" alt="واجهة" width="220" />
</p>

<h1 align="center">واجهة — Wajiha</h1>

<p align="center">
  <strong>مشغّل محاكاة بشاشات مزدوجة لأندرويد</strong><br/>
  بديل لشاشة البداية ومتحكّم بالنظام — مصمَّم لجهاز
  <strong><span dir="ltr">AYN Thor</span></strong><br/>
  (شاشة علوية + سفلية) مع تراجع سلس لشاشة واحدة.<br/>
  تجربة بأسلوب <span dir="ltr">3DS</span> ·
  <span dir="ltr">Kotlin Multiplatform</span> ·
  يُدار أولاً بوحدة التحكم.
</p>

<p align="center">
  <a href="https://github.com/Zyzto/Wajiha/releases/latest"><img alt="release" src="https://img.shields.io/github/v/release/Zyzto/Wajiha?style=flat-square&color=2E7D32" /></a>
  <a href="https://github.com/Zyzto/Wajiha"><img alt="repo" src="https://img.shields.io/badge/github-Zyzto%2FWajiha-C0C0C0?style=flat-square" /></a>
  <a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/Zyzto/Wajiha/releases"><img alt="Obtainium" src="https://img.shields.io/badge/Obtainium-add-2E7D32?style=flat-square&logo=android&logoColor=white" /></a>
  <img alt="android" src="https://img.shields.io/badge/Android-11%2B-2E7D32?style=flat-square&logo=android&logoColor=white" />
  <img alt="kotlin" src="https://img.shields.io/badge/Kotlin-Multiplatform-C0C0C0?style=flat-square&logo=kotlin&logoColor=white" />
  <img alt="thor" src="https://img.shields.io/badge/AYN-Thor-2E7D32?style=flat-square" />
  <img alt="license" src="https://img.shields.io/badge/license-CC%20BY--NC--SA%204.0-2E7D32?style=flat-square" />
</p>

<p align="center">
  <a href="https://github.com/Zyzto/Wajiha/releases/latest">آخر إصدار</a>
  ·
  <a href="docs/architecture.md">الوثائق</a>
</p>

<p align="center">
  <a href="#ماذا-تقدّم">ماذا تقدّم؟</a> ·
  <a href="#التثبيت">التثبيت</a> ·
  <a href="#المتطلبات">المتطلبات</a> ·
  <a href="#البناء">البناء</a> ·
  <a href="#الإعداد-الأول">الإعداد الأول</a> ·
  <a href="#التنقّل-بوحدة-التحكم">وحدة التحكم</a> ·
  <a href="#سلوك-الشاشتين">الشاشتان</a> ·
  <a href="#بنية-المشروع">البنية</a> ·
  <a href="docs/architecture.md">الوثائق</a>
  <br/>
  <a href="README.md"><span dir="ltr">English</span></a>
</p>

<p align="center">
  الاسم من العربية: <strong>واجهة</strong>
  (<span dir="ltr"><em>wājaha</em></span>) — الواجهة / الوجه الأمامي للجهاز المحمول.<br/>
  والاسم اللاتيني <span dir="ltr"><strong>Wajiha</strong></span> مأخوذ منه.
</p>

</div>

---

<div dir="rtl" lang="ar">

## ماذا تقدّم؟

تجمع واجهة أفضل الأنماط من أربعة تطبيقات دُرست
(<span dir="ltr">NeoStation</span>،
<span dir="ltr">Daijishō</span>،
<span dir="ltr">Cocoon</span>،
<span dir="ltr">iiSU</span> — انظر
<span dir="ltr"><code>Study/docs/</code></span>):

- **تجربة بأسلوب <span dir="ltr">3DS</span>** — الشاشة العلوية تعرض غلاف اللعبة المركَّزة / الشعار / البيانات (تخطيط بطل قابل للتخصيص)، والسفلية شبكة ألعاب أفقية مع شريط تثبيت اختياري
- **محرّك شاشات مزدوجة** — آلة حالات مشتركة تقود الشاشتين من عملية واحدة؛ أثناء اللعب يمكن للشاشة الأخرى عرض «قيد التشغيل» أو الإعدادات السريعة أو التطبيقات الجارية أو الساعة أو الإظلام التام
- **اكتشاف التطبيقات الجارية** — حتى الألعاب المُشغَّلة *خارج* واجهة تُكتشف (<span dir="ltr">UsageStats</span> مع خدمة وصول اختيارية) وتُدفع إلى الشاشة الثانوية
- **تشغيل بمستوى <span dir="ltr">NeoStation</span>** — منح <span dir="ltr">SAF URI</span>، إعادة تغليف <span dir="ltr">FileProvider</span>، منح أقراص متعددة، إعدادات نواة <span dir="ltr">RetroArch</span>، وتجاوزات محاكٍ لكل لعبة
- **كشط عميق** — ستّة مصادر (<span dir="ltr">ScreenScraper</span>، <span dir="ltr">SteamGridDB</span>، <span dir="ltr">libretro-thumbnails</span>، <span dir="ltr">RetroAchievements</span>، <span dir="ltr">RomM</span>، ووسائط محلية) مع أولوية مصدر لكل نوع وسائط، سلاسل منطقة/لغة، مهام دفعية، وواجهة مطابقة يدوية — بما فيها أغلفة مربّعة بأسلوب <span dir="ltr">Cocoon</span> بجانب الغلاف التقليدي
- **<span dir="ltr">RetroAchievements</span>** — ربط الألعاب بالتجزئة/العنوان عبر واجهة الويب، لكشط البيانات وتتبع التقدّم

## التثبيت

### أندرويد

| الخيار | |
|--------|--|
| **<span dir="ltr">Obtainium</span>** (موصى به) | [![Obtainium](https://img.shields.io/badge/Obtainium-add-2E7D32?style=flat-square&logo=android&logoColor=white)](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/Zyzto/Wajiha/releases) — يتابع [إصدارات GitHub](https://github.com/Zyzto/Wajiha/releases) |
| **<span dir="ltr">APK</span>** | حمّل <span dir="ltr"><code>Wajiha-VERSION.apk</code></span> من [آخر إصدار](https://github.com/Zyzto/Wajiha/releases/latest) |

## المتطلبات

- أندرويد 11 فما فوق (<span dir="ltr">minSdk 30</span>، نظام Thor)
- <span dir="ltr">JDK 17+</span> و<span dir="ltr">Android SDK</span> (اضبط <span dir="ltr"><code>sdk.dir</code></span> في <span dir="ltr"><code>local.properties</code></span>)
- مشروع <span dir="ltr">Kotlin Multiplatform</span> — أندرويد هو الهدف الوحيد

## البناء

</div>

```bash
./gradlew :androidApp:assembleDebug     # debug APK
./gradlew :androidApp:assembleRelease   # R8-minified release APK (signed if keystore props set)
./gradlew :composeApp:testAndroidHostTest  # host-side unit tests (importers, parsers, scanner)
./gradlew :androidApp:test                 # Android unit tests (ROM path probes)
./gradlew :androidApp:connectedDebugAndroidTest  # instrumented + smoke (device/emulator)
./scripts/ktlint.sh check
./scripts/install-thor.sh               # install debug APK on Thor + launch
./scripts/thor-e2e.sh                   # Thor boot smoke (local / agent; not CI)
```

<div dir="rtl" lang="ar">

انظر <span dir="ltr"><a href="docs/ci.md"><code>docs/ci.md</code></a></span>
لتفاصيل CI وإصدارات GitHub الموقّعة.

تتطلّب واجهة <span dir="ltr">ScreenScraper</span> زوج تطبيق مطوّر
(<span dir="ltr"><code>devid</code></span> /
<span dir="ltr"><code>devpassword</code></span>)
في كل طلب، بالإضافة لتسجيل دخولك في الإعدادات للحصص.
أدخل معرّف المطوّر وكلمة المرور في
<strong>الإعدادات ← الكاشط ← الحسابات</strong>،
أو (للبناء المحلي) في
<span dir="ltr"><code>~/.gradle/gradle.properties</code></span>:

</div>

```properties
wajiha.screenscraper.devid=YOUR_DEV_ID
wajiha.screenscraper.devpassword=YOUR_DEV_PASSWORD
```

<div dir="rtl" lang="ar">

القيم الفارغة تُحذف من الطلبات — لا تُرسل أبدًا كـ
<span dir="ltr"><code>devid=</code></span> فارغ.

## الإعداد الأول

معالج التشغيل الأول يمرّ بكل شيء، وللمرجع:

| الإذن / الدور | لماذا | مطلوب؟ |
|---|---|---|
| تطبيق البداية الافتراضي | امتلاك الشاشتين، والصمود أمام زر HOME | مُستحسَن |
| الوصول للاستخدام | اكتشاف ألعاب شُغّلت خارج واجهة | لاكتشاف التشغيل اليدوي |
| تعديل إعدادات النظام | شريط السطوع / مهلة الشاشة | للإعدادات السريعة |
| الإشعارات | تقدّم المسح والكشط وخدمة الإبقاء حيّة | مُستحسَن |
| الوصول لكل الملفات | مسارات مباشرة للمحاكيات التي ترفض <span dir="ltr"><code>content://</code></span> | اختياري |
| خدمة الوصول | اكتشاف فوري (بالأحداث) بدل استطلاع كل ثانيتين | اختياري |

ثم:

1. **الإعدادات ← المكتبة** — أضف مجلدات ROM لكل منصة (منتقي مجلدات <span dir="ltr">SAF</span>). يبدأ مسح خلفي تلقائيًا؛ تُحسب تجزئات <span dir="ltr">CRC32/MD5</span> بكسل.
2. **الإعدادات ← الكاشط ← الحسابات** (و**المصادر**) — أدخل بيانات المصادر التي تستخدمها (حساب ScreenScraper، مفتاح SteamGridDB، اسم مستخدم RetroAchievements ومفتاح واجهة الويب، خادم RomM، أو مجلد وسائط محلي بتخطيط ES-DE). استخدم **اختبار الدخول** حيث يتوفر. صفوف مركز الكاشط تفتح كصفحات ملء الشاشة (رجوع + عنوان)، لا كعلامات تبويب مجلد متداخلة.
3. **الإعدادات ← الكاشط ← كشط دفعي** — اختر **ملء الفجوات** أو **فرض**، ثم اكشط الكل أو حسب المنصة. يعمل كمهمة <span dir="ltr">WorkManager</span> أمامية مع تقدّم وإيقاف مؤقت/استئناف وإلغاء و**إعادة المحاولة للفاشل** للألعاب الجزئية/الخطأ. تُقسَّم النتائج إلى مطابقة / جزئية / بلا مطابقة / أخطاء، مع قائمة مشاكل قابلة للتوسيع.
4. **المنصة ← الكاشط** — نفس الأوضاع مع **مراجعة** (طابور تفاعلي). تحذيرات عند غياب معرّفات الربط (ScreenScraper / RA / Libretro).
5. **تفاصيل اللعبة ← الكاشط** — ملء فجوات / فرض لمرّة واحدة، أو فتح منتقي المراجعة المشترك لتلك اللعبة.

## التنقّل بوحدة التحكم

دعم كامل لوحدات تحكم الأجهزة المحمولة: تركيز بـ <span dir="ltr">D-pad</span>، تأكيد بـ A، رجوع بـ B، نوافذ طبقية، وأشرطة تلميح لكل شاشة برموز حسب المخطط (تلقائي / Xbox / PlayStation / Switch). انظر [docs/gamepad.md](docs/gamepad.md).

أيقونات الأزرار من **[Kenney Input Prompts](https://kenney.nl/assets/input-prompts)** (<span dir="ltr">CC0</span>) بواسطة [Kenney](https://kenney.nl).

### شبكة المكتبة: تمرير لمس ← D-pad

بعد **تمرير المكتبة باللمس**، **أول ضغطة D-pad** لا تنتقل من التحديد القديم (خارج الشاشة). بل **تثبت** على بلاطة في الصف العلوي ما زالت ظاهرة بوضوح:

- جهة التثبيت تتبع **موضع التحديد عند بدء التمرير** (النصف الأيسر → الأمام / أول ظاهر؛ النصف الأيمن → الخلف / آخر ظاهر) — وليس اتجاه الـ D-pad.
- تُحسب فقط البلاطات الظاهرة بنسبة **≥ ~50%**؛ وعند الإمكان يُفضَّل تثبيت بلاطة حافة **ظاهرة بالكامل** حتى لا يقع التركيز على شريط رفيع.
- بعدها تتحرك ضغطات D-pad طبيعيًا بلاطة تلو الأخرى (تمرير مدفوع بالتحديد، عمود واحد في كل خطوة كحد أقصى).
- النقر على بلاطة يُلغي التثبيت المعلّق فيتحرك D-pad التالي من تلك اللعبة.

القوائم / الإعدادات تستخدم تثبيتًا أبسط: «الهبوط على أعلى صف ظاهر» بعد تمرير اللمس. التفاصيل والتحفظات: [docs/gamepad.md](docs/gamepad.md#touch-scroll--d-pad-snap) (قد يخضع هذا المسار لصقل إضافي).

## تفاصيل اللعبة وإعدادات المنصة

- **تفاصيل اللعبة** — بيانات ووسائط وخيارات تشغيل وسجل لعب لكل لعبة (<span dir="ltr"><code>GameDetailScreen</code></span>)
- **إعدادات المنصة** — مجلدات ROM وتجاوزات المحاكي والكاشط لكل منصة (<span dir="ltr"><code>PlatformSettingsScreen</code></span>)

## مواءمة ROM

عند تفعيل **الإعدادات ← المكتبة ← اكتشاف الألعاب الخارجية**، تستكشف واجهة ملفات بيانات المحاكيات (سجل RetroArch، وقت لعب AetherSX2) لمطابقة ألعاب شُغّلت خارج المشغّل.

## جلسات متعددة — قيد التشغيل الآن

جلسات المحاكي المتزامنة تظهر كبلاطات في شبكة المكتبة؛ اضغط للتبديل، وY للإغلاق. يُتتبَّع وقت اللعب لكل حزمة. انظر [docs/sessions.md](docs/sessions.md). تصحيح Thor: [docs/debug.md](docs/debug.md).

## المظهر والمنزل

- **الإعدادات ← المظهر ← إظهار شريط المنزل** — شريط تثبيت فوق تلميحات وحدة التحكم (<span dir="ltr"><code>WajihaDock</code></span>)؛ التثبيتات تطابق مفضّلات التطبيقات. اختصارات التطبيقات / الإعدادات على الشريط تُخفي إجراءات الواجهة المكررة عند التفعيل (افتراضيًا مفعّل).
- **الإعدادات ← المظهر ← حزمة الأيقونات / شكل الأيقونة** — حزم بأسلوب Nova/ADW لدرج التطبيقات والشريط. الأشكال: النظام، دائرة، Squircle، مربّع مستدير، مربّع.
- **الإعدادات ← المظهر ← شبكة الألعاب** — تفضيلات لكل شاشة (أي شاشة تستضيف الإعدادات): أسلوب الفن غلاف / أيقونة / شعار، صفوف 2–5، حجم بلاطة S/M/L، إظهار العناوين، إطار + تدرّج. **الأيقونة** تفضّل فن <span dir="ltr"><code>square</code></span> المكسوح ثم <span dir="ltr"><code>icon</code></span>.
- **الإعدادات ← الشاشات ← تخطيط لوحة البطل** — إعدادات مسبقة (كلاسيكي، تركيز الغلاف، تركيز الشعار، أدنى، نص فقط، فارغ) مع محرّر **تخصيص التخطيط** للشاشة التي تعرض لوحة البطل حاليًا. خيار **خلفية بطل اللعبة المركَّزة** يرسم فن البطل خلف شبكة الألعاب.

## تفاصيل الكاشط

إعدادات الكاشط مركز لصفحات ملء الشاشة: **كشط دفعي**، **المصادر**، **الحسابات**، **افتراضات الوسائط**، **خيارات الدفعة**.

- **أداة المطابقة** — ملء الفجوات / فرض / مراجعة تمرّ كلها عبر <span dir="ltr"><code>ScrapeMatchTool</code></span>: بحث المصادر، ترتيب بالثقة + الدرجة + تفضيل/حظر المؤلف، ثم اختيار تلقائي أو تسليم القائمة للمراجعة.
- **البيانات الوصفية** تأتي من أول مصدر في سلسلة الأولوية طابق (الافتراضي: ScreenScraper → RomM → RA). تُجرَّب تجزئات CRC32/MD5 قبل البحث بالاسم.
- **الوسائط** تُحلّ لكل نوع مع ترتيب (الثقة / الدرجة / تفضيلات المؤلف قد تتجاوز رقائق أولوية المصدر الافتراضية). الأنواع تشمل الغلاف، **مربّع** (شبكات SteamGridDB 1:1 بجانب الغلاف)، الشعار، البطل، لقطة، فن جماهيري، فيديو، أيقونة، لافتة.
- **سلاسل تفضيل المنطقة واللغة** تُطبَّق على الأسماء والملخصات ومتغيرات الوسائط حسب المنطقة.
- **حد حجم الصورة** — الصور أكبر من الدقة القصوى المضبوطة (**افتراضات الوسائط**، افتراضيًا 1024 بكسل لأطول ضلع، 0 = الإبقاء) تُصغَّر وتُضغط مجددًا (PNG للشعارات/الأيقونات، JPEG لغيرها) قبل التخزين.
- **تجاوزات لكل منصة** (**المصادر**، أسفل) — اختر منصة لتجاوز مصادرها وأولوية منطقتها؛ الحقول غير المعيَّنة ترث الخيارات العامة. المنصات المتجاوَزة تُعلَّم بـ <span dir="ltr"><code>*</code></span>.
- **أوضاع الكشط** (لكل تشغيل):
  - **ملء الفجوات** — ألعاب ينقصها بيانات أو فن مفضّل (غلاف / مربّع / شعار / بطل) فقط؛ لا يستبدل وسائط موجودة. الدفعة والمنصة وتفاصيل اللعبة تعرض أنواع الفجوات في النص الداعم؛ مصغّرات مرشّحي المراجعة تفضّل المربّع عند وجوده.
  - **فرض** — إعادة كشط الكل واستبدال البيانات والوسائط.
  - **مراجعة** — واجهة فقط: ابحث عن مرشّحين، اختر البيانات وكل خانة وسائط (بما فيها المربّع)، ثم طبّق / تخطَّ. من المنصة ← الكاشط (طابور) وتفاصيل اللعبة. لا تُدرَج في WorkManager.
- **النتائج** — كل لعبة تنتهي كمطابقة، جزئية (مطابقة لكن فشل تنزيل الوسائط)، بلا مطابقة، أو خطأ (مصادقة / شبكة / حد معدّل / إعداد). واجهة الدفعة والإشعارات تعرض أعدادًا مقسّمة ومشاكل لكل لعبة؛ **إعادة المحاولة للفاشل** تعيد تشغيل ألعاب الخطأ + الجزئي من آخر تشغيل.
- **الوسائط المحلية** تستخدم تخطيط مجلدات ES-DE: <span dir="ltr"><code>&lt;root&gt;/&lt;platform id&gt;/&lt;covers|marquees|screenshots|fanart|videos|...&gt;/&lt;rom base name&gt;.&lt;ext&gt;</code></span> (لا يوجد بعد تعيين مجلد مربّع مخصّص).
- **تقدّم الدفعة يصمد أمام إعادة التشغيل** — يُحفَظ بعد كل لعبة؛ إن ماتت العملية أثناء التشغيل، تستأنف مهمة WorkManager المعاد تشغيلها أعداد ملء الفجوات وتعرض الواجهة ملخّص آخر تشغيل بعد إعادة تشغيل التطبيق.

## سلوك الشاشتين

آلة الحالات (انظر <span dir="ltr"><code>DualScreenStore</code></span>):
<span dir="ltr"><code>SingleDisplay</code></span>،
<span dir="ltr"><code>DualBrowsing</code></span>،
<span dir="ltr"><code>GameRunning</code></span>،
<span dir="ltr"><code>AppOnSecondary</code></span>،
<span dir="ltr"><code>BlackoutSecondary</code></span>.

- أثناء التصفّح: الأعلى = لوحة البطل (<span dir="ltr"><code>HeroCanvas</code></span> + <span dir="ltr"><code>HeroLayout</code></span> لكل شاشة)، الأسفل = شبكة الألعاب + شريط منزل اختياري (الأدوار قابلة للتبادل في الإعدادات ← الشاشات؛ خيار **تبديل تلميحات وحدة التحكم** ينقل شريط التحكم إلى لوحة البطل).
- أثناء اللعب: الشاشة الأخرى تعرض الوضع الذي اخترته — قيد التشغيل، إعدادات سريعة، تطبيقات جارية، ساعة، أو إظلام (ومتاح أيضًا كإظلام عند الإطلاق). كل وضع غير الشبكة له رأس بعلامات تبويب لتبديل الأوضاع أو العودة للشبكة؛ الشاشة المظلمة تُستعاد باللمس. التطبيقات على الشاشة الثانوية تستخدم درج التطبيقات (مسار <span dir="ltr"><code>AppDock</code></span>).
- خدمة أمامية <span dir="ltr"><code>KeepAliveService</code></span> تُبقي حالة المشغّل أثناء لعبة خارجية؛ العودة إلى واجهة تُغلق جلسة اللعب وتسجّل وقت اللعب.
- على أجهزة الشاشة الواحدة ينطوي كل شيء في تخطيط عمودي موحّد بأسلوب 3DS.

## بنية المشروع

</div>

```
composeApp/            shared KMP module (UI, domain, data)
  src/commonMain/kotlin/com/wajiha/
    data/db/           Room KMP entities, DAOs, database
    data/config/       unified platform schema + Daijishō/iiSU importers
    data/prefs/        app settings (DataStore), hero layouts, game-grid prefs
    data/scraper/      scrape engine, batch job, 6 scraper sources
    data/ra/           RetroAchievements client + repository
    domain/            repositories, library scanner, GamingAppCatalog
    state/             DualScreenStore (dual-screen state machine)
    input/             gamepad nav controller, layer stack, keys
    ui/                home (+ hero/), secondary, apps, settings, scraper, ra,
                       system, onboarding, gamedetail, running, theme,
                       components/gamepad, WajihaDock, navigation
    platform/          host-bound interfaces (AppActions, SystemControls, ...)
  src/androidMain/     Android actuals (SAF scanner, hasher, media storage)

androidApp/            Android host application
  .../MainActivity     HOME + LAUNCHER (primary display)
  .../SecondaryHomeActivity  SECONDARY_HOME (bottom display)
  .../launch/          EmulatorLauncher, GameLauncher, PlaySessionTracker
  .../display/         DisplayCoordinator
  .../monitor/         ForegroundAppMonitor, GameSessionController
  .../detect/          ExternalGameResolver, ROM path probes
  .../input/           GamepadKeyRouter, LauncherKeyRouting
  .../service/         KeepAliveService
  .../system/          SystemController, BootReceiver
  .../work/            LibraryScanWorker, ScrapeWorker
  .../library/         RomFolderManager, RomFileDeleter

docs/                  architecture, gamepad, sessions, debug, external-apis
Study/                 dissection docs + reference apps (not shipped)
```

<div dir="rtl" lang="ar">

البنية الكاملة: [docs/architecture.md](docs/architecture.md). نموذج الجلسات: [docs/sessions.md](docs/sessions.md). تصحيح Thor: [docs/debug.md](docs/debug.md).

## الفجوات المعروفة

- **المجموعات** — مخطط قاعدة البيانات و<span dir="ltr"><code>CollectionRepository</code></span> موجودان؛ الواجهة غير منفَّذة بعد.

## الرخصة

[CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/) — مشاركة وتعديل مع الإسناد، **لغير الاستخدام التجاري** فقط، ونفس الرخصة للمشتقات.  
النص الكامل: [LICENSE](LICENSE).

</div>
