-- Social media profile links, one per line; a customer's display name there often differs from their real name.
ALTER TABLE customer ADD COLUMN social_media text CHECK (char_length(social_media) <= 1000);
