
INSERT INTO categories (id, name, slug, parent_id, sort_order, deleted, created_at, updated_at)
VALUES ('9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb7b', 'Khác', 'khac', NULL, 999, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (slug) DO NOTHING;


INSERT INTO categories (id, name, slug, parent_id, sort_order, deleted, created_at, updated_at)
VALUES ('4c8d5e8f-7b7d-4c8d-9b5d-3b2d6b1d4c3f', 'Sản phẩm khác', 'san-pham-khac', '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb7b', 1, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (slug) DO NOTHING;
