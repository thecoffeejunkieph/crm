-- Social media profile links (one per line); display names often differ from real names.
ALTER TABLE customers ADD COLUMN social_media varchar(1000) DEFAULT NULL;
