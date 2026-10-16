import '../services/l10n/locale_controller.dart';
import 'platform_channels.dart';
import 'version_info.dart';

/// §362 — единственный источник ссылок проекта. До этого адреса лежали
/// копиями по экранам (About, Automation-tab, update_checker) и в текстах
/// support-ленты — смена адреса требовала обхода всех мест.
///
/// Здесь же — подстановка `@плейсхолдеров` в remote-контенте (support.json):
/// автор пишет `"url": "@guideLink"` вместо адреса, приложение подставляет
/// актуальное значение — в том числе локале-зависимое (гайд ru/en).
class ProjectLinks {
  ProjectLinks._();

  /// Hapai публикуется в ветке [ghBranch] репозитория Lumix-Team/hapai: в его
  /// `main` лежит другой проект, поэтому и «Source Code», и ссылки на файлы
  /// указывают на ветку. Переезд (в `main`, в отдельный репозиторий или
  /// переименование ветки) — правка двух констант ниже.
  static const ghRepo = 'https://github.com/Lumix-Team/hapai';
  static const ghBranch = 'lxbox-fork';

  static const repo = '$ghRepo/tree/$ghBranch';
  static const latestRelease = '$ghRepo/releases/latest';
  static const core = 'https://github.com/Leadaxe/sing-box-lx';
  static const launcher = 'https://github.com/Leadaxe/singbox-launcher';
  static const singboxUpstream = 'https://github.com/SagerNet/sing-box';
  static const telegram = 'https://t.me/singbox_launcher/4317';
  static const donate = 'https://t.me/singbox_launcher/340/3621';
  static const issues = '$ghRepo/issues';
  static const donatePage = '$ghRepo/blob/$ghBranch/docs/DONATE.md';
  static const donatePageRu = '$ghRepo/blob/$ghBranch/docs/DONATE.ru.md';

  /// Страница поддержки на языке интерфейса (пара RU/EN, как гайд).
  static String donatePageFor(String tag) =>
      tag == 'ru' ? donatePageRu : donatePage;
  static const automationDoc = '$ghRepo/blob/$ghBranch/docs/AUTOMATION.md';

  /// §361 — руководство пользователя. Пара RU/EN держится синхронной
  /// CI-проверкой парности (tool/docs/parity_check.dart). Ветка [ghBranch]:
  /// оба файла гайда лежат в том же коммите, что и код. Незнакомый тег →
  /// английская версия, а не 404.
  static const guideEn = '$ghRepo/blob/$ghBranch/docs/USER_GUIDE.md';
  static const guideRu = '$ghRepo/blob/$ghBranch/docs/USER_GUIDE.ru.md';

  static String guideFor(String tag) => tag == 'ru' ? guideRu : guideEn;

  static String releaseTag(String tag) => '$ghRepo/releases/tag/$tag';

  /// §390 — страницы приложения в сторах. Куда вести за обновлением, решает
  /// канал установки (`InstallSource.updateUrl`): APK с GitHub не встанет
  /// поверх Play-сборки, подписи разные.
  ///
  /// `market://` открывает клиент Play напрямую. Если Play на устройстве нет —
  /// intent не резолвится, native `openUrl` падает на [playPageWeb].
  static const playPage =
      'market://details?id=${PlatformChannels.packageName}';
  static const playPageWeb =
      'https://play.google.com/store/apps/details?id=${PlatformChannels.packageName}';
  static const fdroidPage =
      'https://f-droid.org/packages/${PlatformChannels.packageName}/';

  /// Плейсхолдеры remote-контента. Значения резолвятся В МОМЕНТ показа:
  /// `@guideLink` зависит от текущей локали, `@appVersion` — от версии APK.
  static Map<String, String> placeholders() => {
        '@selfLink': latestRelease,
        '@repoLink': repo,
        '@coreLink': core,
        '@launcherLink': launcher,
        '@tgLink': telegram,
        '@donateLink': donate,
        '@issuesLink': issues,
        '@donatePage': donatePageFor(LocaleController.I.effectiveTag),
        '@guideLink': guideFor(LocaleController.I.effectiveTag),
        '@appVersion': VersionInfo.I.version,
      };

  /// Подстановка `@плейсхолдеров` в произвольной строке (url/label/message).
  /// Неизвестный `@токен` остаётся текстом как есть — опечатка автора не
  /// ломает сообщение и видна глазом. Длинные имена подставляются первыми,
  /// чтобы префикс не съедал более длинный ключ.
  static String expand(String raw) {
    if (!raw.contains('@')) return raw;
    final map = placeholders();
    final keys = map.keys.toList()
      ..sort((a, b) => b.length.compareTo(a.length));
    var out = raw;
    for (final k in keys) {
      if (out.contains(k)) out = out.replaceAll(k, map[k]!);
    }
    return out;
  }
}
