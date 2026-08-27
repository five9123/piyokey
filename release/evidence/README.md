# Verification evidence

각 JSON은 파일명과 `verified_commit`에 적힌 소스 commit에서 실행한 로컬 검증 결과다. 증빙 생성 뒤 코드가 바뀌면 기존 파일을 수정하지 않고 새 commit SHA로 다시 생성한다.

```bash
python3 tools/verification_evidence.py \
  --scope repository \
  --check 'workspace-doctor::pass::python3 tools/workspace_doctor.py' \
  --check 'release-preflight::pass::python3 tools/release_preflight.py' \
  --manual-gate '실기기 또는 스토어 gate가 있으면 적는다'
```

증빙 파일은 검증 대상 코드 commit 다음의 evidence-only commit으로 추가한다. PR 설명에는 검증 대상 SHA와 증빙 파일을 함께 링크하고, 이후 코드 변경이 있으면 이전 증빙으로 병합하지 않는다.
