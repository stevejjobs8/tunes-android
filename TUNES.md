# 튠즈 (개인용)

Metrolist를 포크해 만든 개인용 유튜브 뮤직 앱. 배포하지 않고 만든 이의 폰에서만 쓴다.

- 설치 파일: `main`에 push하면 `.github/workflows/tunes.yml`이 빌드해서 Releases의 `tunes-latest`에 `tunes.apk`로 올린다.
- 앱 ID `com.stevejjobs8.tunes`, 이름 `튠즈` (워크플로의 환경 변수로 지정 — 업스트림 파일은 건드리지 않음). 원래 Metrolist와 따로 깔린다.
- 변형은 `izzy`(업데이트 확인 없음 — Metrolist 업데이트 알림이 뜨지 않게).
- 서명: 저장소 시크릿 `TUNES_KEYSTORE_B64`, `TUNES_KEYSTORE_PASSWORD`(별칭 `tunes`). 없으면 매번 임시 키로 서명돼서 새 APK를 덮어 깔 수 없다.
- 업스트림 워크플로(build·build_pr·build_quick·release)는 유료 러너와 Metrolist 서명이 필요해 잡마다 `if: github.repository == 'MetrolistGroup/Metrolist'`를 붙여 이 포크에선 건너뛴다(업스트림을 합칠 때 충돌나면 다시 붙일 것).
- 업스트림 따라가기: `git fetch https://github.com/MetrolistGroup/Metrolist main && git merge FETCH_HEAD`. 업스트림 파일은 되도록 적게 고친다.
