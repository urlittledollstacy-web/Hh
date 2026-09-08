from pathlib import Path
main = Path('app/src/main/java/app/hikari/MainActivity.kt')
media = Path('app/src/main/java/app/hikari/media/MediaDetailsScreen.kt')
checks = [
    ('main_exists', main.exists()),
    ('hikari', '@Composable private fun HikariApp' in main.read_text()),
    ('appcontent', '@Composable private fun AppContent' in main.read_text()),
    ('media_exists', media.exists()),
    ('media_fn', '@Composable fun MediaDetailsScreen' in media.read_text()),
    ('tracking', '@Composable private fun TrackingLoadingDialog' in media.read_text()),
]
Path('.github/patch-debug.txt').write_text('\n'.join(f'{k}={v}' for k, v in checks))
