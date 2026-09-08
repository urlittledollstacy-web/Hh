from pathlib import Path
import re, runpy
p = Path('.github/apply-navigation-fix.py')
s = p.read_text()
s = re.sub(
    r"pattern = r'@Composable private fun HikariApp.*?replacement = new_hikari \\+ '\\n@Composable private fun AppContent'",
    "pattern = r'@Composable private fun HikariApp.*?@Composable private fun AppContent'\nreplacement = new_hikari + '\\n@Composable private fun AppContent'",
    s, count=1, flags=re.S)
s = re.sub(
    r"pattern = r'@Composable fun MediaDetailsScreen.*?replacement = new_media \\+ '@Composable private fun TrackingLoadingDialog'",
    "pattern = r'@Composable fun MediaDetailsScreen.*?@Composable private fun TrackingLoadingDialog'\nreplacement = new_media + '@Composable private fun TrackingLoadingDialog'",
    s, count=1, flags=re.S)
p.write_text(s)
runpy.run_path(str(p))
