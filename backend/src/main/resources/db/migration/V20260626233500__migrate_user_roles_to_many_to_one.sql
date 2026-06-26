
ALTER TABLE users ADD COLUMN role_id VARCHAR(36);


UPDATE users u
SET role_id = (
    SELECT r.id
    FROM user_roles ur
    JOIN roles r ON ur.role_id = r.id
    WHERE ur.user_id = u.id
    ORDER BY
        CASE r.name
            WHEN 'ROLE_ADMIN' THEN 1
            WHEN 'ROLE_CREATOR' THEN 2
            WHEN 'ROLE_USER' THEN 3
            ELSE 4
        END ASC
    LIMIT 1
);


UPDATE users
SET role_id = (SELECT id FROM roles WHERE name = 'ROLE_USER')
WHERE role_id IS NULL;


ALTER TABLE users ALTER COLUMN role_id SET NOT NULL;


ALTER TABLE users ADD CONSTRAINT fk_users_role_id FOREIGN KEY (role_id) REFERENCES roles(id);


DROP TABLE IF EXISTS user_roles;
