<!-- markdownlint-disable MD033 MD060 -->

<div dir="rtl" lang="ar">

<p align="center">
  <img src="assets/Logo.svg" alt="واجهة" width="200" />
</p>

<h1 align="center">واجهة — Wajiha</h1>

<p align="center">
  <strong>مشغّل محاكاة بشاشات مزدوجة لأندرويد.</strong><br/>
  تطبيق بداية لجهاز <span dir="ltr">AYN Thor</span>: لوحة البطل على شاشة،
  والمكتبة على الأخرى.
  <span dir="ltr">Kotlin Multiplatform</span> · يُدار بوحدة التحكم ·
  عملية واحدة للشاشتين.
</p>

<p align="center">
  <a href="https://github.com/Shenepoy/Wajiha/releases/latest"><img alt="release" src="https://img.shields.io/github/v/release/Shenepoy/Wajiha?style=flat-square&color=2E7D32" /></a>
  <a href="https://github.com/Shenepoy/Wajiha/actions/workflows/ci.yml"><img alt="CI" src="https://img.shields.io/github/actions/workflow/status/Shenepoy/Wajiha/ci.yml?style=flat-square&label=CI" /></a>
  <a href="https://github.com/Shenepoy/Wajiha"><img alt="repo" src="https://img.shields.io/badge/github-Shenepoy%2FWajiha-C0C0C0?style=flat-square" /></a>
  <a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/Shenepoy/Wajiha/releases"><img alt="Obtainium" src="https://img.shields.io/badge/Obtainium-add-2E7D32?style=flat-square&logo=android&logoColor=white" /></a>
  <img alt="android" src="https://img.shields.io/badge/Android-11%2B-2E7D32?style=flat-square&logo=android&logoColor=white" />
  <img alt="kotlin" src="https://img.shields.io/badge/Kotlin-Multiplatform-C0C0C0?style=flat-square&logo=kotlin&logoColor=white" />
  <a href="LICENSE"><img alt="license" src="https://img.shields.io/badge/license-AGPL--3.0-2E7D32?style=flat-square" /></a>
</p>

<p align="center">
  <a href="https://github.com/Shenepoy/Wajiha/releases/latest"><strong>آخر إصدار</strong></a>
  ·
  <a href="#ماذا-تقدّم">ماذا تقدّم</a>
  ·
  <a href="#التثبيت">التثبيت</a>
  ·
  <a href="#التطوير">التطوير</a>
  ·
  <a href="#الوثائق">الوثائق</a>
  ·
  <a href="README.md"><span dir="ltr">English</span></a>
</p>

<p align="center">
  الاسم <strong>واجهة</strong>
  (<span dir="ltr"><em>wājaha</em></span>) يعني الواجهة —
  وجه الجهاز المحمول.
  والاسم اللاتيني <span dir="ltr"><strong>Wajiha</strong></span> مأخوذ منه.
</p>

---

## ماذا تقدّم

المكتبة تبقى على الجهاز. الشاشة الواحدة تستخدم تخطيطًا موحّدًا.

| | |
|---|---|
| **المكتبة** | فن البطل على شاشة، وشبكة الألعاب على الأخرى. يمكن تبديل الدورين. |
| **التشغيل** | المحاكيات، ومنها <span dir="ltr">RetroArch</span>، مع تجاوز لكل لعبة. |
| **الغلاف** | <span dir="ltr">ScreenScraper</span> و<span dir="ltr">SteamGridDB</span> و<span dir="ltr">libretro</span> و<span dir="ltr">RetroAchievements</span> و<span dir="ltr">RomM</span> أو مجلد محلي. |
| **قيد التشغيل** | لعبة تُفتح خارج واجهة تبقى ظاهرة على الشاشة الأخرى. |
| **وحدة التحكم** | تركيز بـ <span dir="ltr">D-pad</span>، وأزرار الوجه، وشريط تلميح. |
| **المجموعات** | تجميع الألعاب داخل المكتبة. ملفات <span dir="ltr">ROM</span> تبقى في مكانها. |

---

## التثبيت

### أندرويد

| الخيار | |
|--------|--|
| **<span dir="ltr">Obtainium</span>** (موصى به) | [![Obtainium](https://img.shields.io/badge/Obtainium-add-2E7D32?style=flat-square&logo=android&logoColor=white)](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/Shenepoy/Wajiha/releases) — يتابع [إصدارات GitHub](https://github.com/Shenepoy/Wajiha/releases) |
| **<span dir="ltr">APK</span>** | من [آخر إصدار](https://github.com/Shenepoy/Wajiha/releases/latest) |

<span dir="ltr"><code>Wajiha-&lt;version&gt;.apk</code></span> هو البناء الشامل.
كل إصدار فيه أيضًا ملف لكل معمارية.
أندرويد 11 فما فوق.

---

## التطوير

**المتطلبات:** <span dir="ltr">JDK 21</span> و<span dir="ltr">Android SDK</span>
(<span dir="ltr"><code>sdk.dir</code></span> في <span dir="ltr"><code>local.properties</code></span>).

</div>

```bash
git clone https://github.com/Shenepoy/Wajiha.git
cd Wajiha
./gradlew :androidApp:assembleDebug
```

```bash
./scripts/ktlint.sh check
./gradlew :composeApp:testAndroidHostTest :androidApp:test
```

<div dir="rtl" lang="ar">

بيانات <span dir="ltr">ScreenScraper</span> تُدخل من
<strong>الإعدادات ← الكاشط ← الحسابات</strong>.
التكامل المستمر والتوقيع والإصدارات الموسومة في
[docs/ci.md](docs/ci.md).
تثبيت <span dir="ltr">Thor</span> والسجلات في
[docs/debug.md](docs/debug.md).

## بنية المشروع

- <span dir="ltr"><code>composeApp/</code></span> — الواجهة، والمنطق، وقاعدة البيانات، والكاشط.
- <span dir="ltr"><code>androidApp/</code></span> — مضيف أندرويد: الأنشطة، والتشغيل، والشاشات، والمهام.
- <span dir="ltr"><code>docs/</code></span> — البنية، والتصميم، ووحدة التحكم، والجلسات، والتكامل المستمر.
- <span dir="ltr"><code>Study/</code></span> — ملاحظات مرجعية بجانب التطبيق.

الشاشتان تعملان في عملية واحدة. الحالة المشتركة
<span dir="ltr"><code>StateFlow</code></span>، والربط عبر
<span dir="ltr">Koin</span>.

---

## الوثائق

| الدليل | |
|-------|--|
| [البنية](docs/architecture.md) | الوحدات، والبيانات، وآلة حالات الشاشتين |
| [التصميم](docs/design.md) | الواجهة المشتركة وكيف تُبنى الشاشات |
| [وحدة التحكم](docs/gamepad.md) | التركيز، والتلميحات، وتثبيت التمرير باللمس |
| [الجلسات](docs/sessions.md) | قيد التشغيل ووقت اللعب |
| [الواجهات الخارجية](docs/external-apis.md) | مصادر الكشط و<span dir="ltr">RetroAchievements</span> |
| [التكامل والإصدارات](docs/ci.md) | الفحص، والاختبارات، وملفات <span dir="ltr">APK</span> الموقّعة، و<span dir="ltr">Obtainium</span> |
| [تصحيح Thor](docs/debug.md) | التثبيت والسجلات على الجهاز |

---

## الرخصة

[GNU AGPL-3.0](LICENSE).

رموز الأزرار من [Kenney Input Prompts](https://kenney.nl/assets/input-prompts)
(<span dir="ltr">CC0</span>) بواسطة [Kenney](https://kenney.nl).

---

<p align="center">
  من <a href="https://shenepoy.com"><strong>shenepoy</strong></a>
  ·
  <a href="https://github.com/Shenepoy"><span dir="ltr">GitHub</span></a>
</p>

</div>
