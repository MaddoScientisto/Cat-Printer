#!/usr/bin/env python3
import re
import shutil
import subprocess
from pathlib import Path


WORKSPACE = Path('/workspace')
ADVANCED_WEBVIEW_SOURCE = WORKSPACE / 'build-android' / 'advancedwebview'
ADVANCED_WEBVIEW_FALLBACK = Path('/opt/cat-printer-build/Android-AdvancedWebView/Source/library/src/main/java')


def find_python_for_android_dir():
    result = subprocess.run(
        ['python3', '-c', 'import os, pythonforandroid; print(os.path.dirname(pythonforandroid.__file__))'],
        check=True,
        text=True,
        capture_output=True,
    )
    return Path(result.stdout.strip())


def install_bleak_recipe(p4a_dir):
    recipe_source = Path('/opt/cat-printer-build/bleak/bleak/backends/p4android/recipes/bleak')
    recipe_target = p4a_dir / 'recipes' / 'bleak'

    if not recipe_source.exists():
        raise FileNotFoundError(f'Bleak p4android recipe not found at {recipe_source}')

    if recipe_target.is_symlink() or recipe_target.exists():
        return

    recipe_target.symlink_to(recipe_source, target_is_directory=True)


def copy_advanced_webview(p4a_dir):
    source = ADVANCED_WEBVIEW_SOURCE if ADVANCED_WEBVIEW_SOURCE.exists() else ADVANCED_WEBVIEW_FALLBACK
    if not source.exists():
        raise FileNotFoundError(f'AdvancedWebView source not found at {ADVANCED_WEBVIEW_SOURCE} or {ADVANCED_WEBVIEW_FALLBACK}')

    java_target = p4a_dir / 'bootstraps' / 'webview' / 'build' / 'src' / 'main' / 'java'
    shutil.copytree(source, java_target, dirs_exist_ok=True)
    patch_advanced_webview(java_target / 'im' / 'delight' / 'android' / 'webview' / 'AdvancedWebView.java')
    patch_cached_advanced_webview_sources(p4a_dir)


def patch_advanced_webview(advanced_webview_path):
    text = advanced_webview_path.read_text(encoding='utf-8')
    patched_text = remove_java_method(text, 'onUnhandledInputEvent')
    patched_text = remove_java_method(patched_text, 'onReachedMaxAppCacheSize')
    advanced_webview_path.write_text(patched_text, encoding='utf-8')


def patch_cached_advanced_webview_sources(p4a_dir):
    for root in (p4a_dir, Path('/root/.local/share/python-for-android')):
        if not root.exists():
            continue
        for advanced_webview_path in root.glob('**/im/delight/android/webview/AdvancedWebView.java'):
            patch_advanced_webview(advanced_webview_path)


def remove_java_method(text, method_name):
    method_marker = f'public void {method_name}('
    search_from = 0

    while True:
        method_index = text.find(method_marker, search_from)
        if method_index == -1:
            return text

        override_index = text.rfind('@Override', 0, method_index)
        line_start = text.rfind('\n', 0, method_index) + 1
        removal_start = override_index if override_index >= line_start - 32 else line_start
        brace_index = text.find('{', method_index)
        if brace_index == -1:
            search_from = method_index + len(method_marker)
            continue

        depth = 0
        for index in range(brace_index, len(text)):
            if text[index] == '{':
                depth += 1
            elif text[index] == '}':
                depth -= 1
                if depth == 0:
                    removal_end = index + 1
                    if removal_end < len(text) and text[removal_end] == '\n':
                        removal_end += 1
                    text = text[:removal_start] + text[removal_end:]
                    search_from = removal_start
                    break
        else:
            return text


def patch_python_activity(p4a_dir):
    activity_path = p4a_dir / 'bootstraps' / 'webview' / 'build' / 'src' / 'main' / 'java' / 'org' / 'kivy' / 'android' / 'PythonActivity.java'
    if not activity_path.exists():
        raise FileNotFoundError(f'python-for-android webview PythonActivity.java not found at {activity_path}')

    text = activity_path.read_text(encoding='utf-8')

    if 'import im.delight.android.webview.AdvancedWebView;' not in text:
        text = text.replace('import android.webkit.WebView;', 'import im.delight.android.webview.AdvancedWebView;')
        text = re.sub(r'\bWebView\b', 'AdvancedWebView', text)

    text = re.sub(
        r'(?m)^[ \t]*@Override\n(?=[ \t]*public (?:boolean shouldOverrideUrlLoading|void onPageFinished)\b)',
        '',
        text,
    )

    if 'requestCode == 51426' not in text:
        def add_activity_result_handler(match):
            return (
                match.group(1)
                + '        if ( requestCode == 51426 ) {\n'
                + '            mWebView.onActivityResult(requestCode, resultCode, intent);\n'
                + '            return;\n'
                + '        }\n'
            )

        patched_text = re.sub(
            r'(^[ \t]*(?:protected|public) void onActivityResult\([^\n]+\) \{[ \t]*\n)',
            add_activity_result_handler,
            text,
            count=1,
            flags=re.MULTILINE,
        )
        if patched_text == text:
            raise RuntimeError(f'Could not patch onActivityResult in {activity_path}')
        text = patched_text

    activity_path.write_text(text, encoding='utf-8')


def copy_loading_assets(p4a_dir):
    includes_dir = p4a_dir / 'bootstraps' / 'webview' / 'build' / 'webview_includes'
    includes_dir.mkdir(parents=True, exist_ok=True)

    for filename in ('_load.html', 'icon.svg'):
        shutil.copy2(WORKSPACE / 'www' / filename, includes_dir / filename)


def main():
    p4a_dir = find_python_for_android_dir()
    install_bleak_recipe(p4a_dir)
    copy_advanced_webview(p4a_dir)
    patch_python_activity(p4a_dir)
    copy_loading_assets(p4a_dir)


if __name__ == '__main__':
    main()


