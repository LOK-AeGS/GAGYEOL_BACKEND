-- groups 테이블: 지출인(계좌 주인) 정보 추가
ALTER TABLE groups ADD COLUMN payer_name VARCHAR(100);
ALTER TABLE groups ADD COLUMN payer_affiliation VARCHAR(200);
ALTER TABLE groups ADD COLUMN payer_student_id VARCHAR(50);
ALTER TABLE groups ADD COLUMN payer_phone VARCHAR(50);
