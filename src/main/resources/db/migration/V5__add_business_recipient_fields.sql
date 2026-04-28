-- form 테이블: LLM으로 생성할 필드 목록 추가
ALTER TABLE form ADD COLUMN generated_fields TEXT;

-- evidence 테이블: 사업명 + 수령인 정보 사진 경로 추가
ALTER TABLE evidence ADD COLUMN business_name TEXT;
ALTER TABLE evidence ADD COLUMN recipient_image_path TEXT;
