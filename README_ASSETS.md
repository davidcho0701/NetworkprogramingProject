에셋(이미지) 배치 안내

프로젝트 루트에 `resources/` 폴더를 만들고, 아래 경로/파일명으로 이미지를 넣어주세요. 경로/이름이 정확히 일치해야 코드가 자동으로 로드합니다.

폴더 구조

resources/
assets/
tiles/
floor_school.png (선택)
floor_construction.png (선택)
floor_military.png (선택)
floor_custom.png (단일 배경 이미지. 없으면 타일 방식 사용)
props/
box.png
barrel.png
cone.png
tire.png
crate.png
chair.png
table.png
plant.png
lamp.png
book.png
players/
seeker.png (술래 캐릭터)
effects/
muzzle.png (선택)

주의

- IntelliJ: Project Structure → Modules → Sources 탭에서 `resources/` 를 Resources Root로 지정하세요.
- 이미지 크기 권장: 48~64px. 코드는 48px로 스케일해 그립니다.
- 배경은 `floor_테마.png`가 있으면 64px 타일로 채우며, `floor_custom.png`가 있으면 패널 크기에 맞춰 한 장으로 채웁니다.
