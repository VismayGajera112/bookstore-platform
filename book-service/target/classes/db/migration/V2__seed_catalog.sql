-- A small starter catalog so the platform is usable the moment it boots. Idempotent on re-run.
INSERT INTO author (name) VALUES
    ('Robert C. Martin'),
    ('Eric Evans'),
    ('Martin Fowler'),
    ('Sam Newman')
ON CONFLICT DO NOTHING;

INSERT INTO book (title, author_id, isbn, price, stock)
SELECT v.title, a.id, v.isbn, v.price, v.stock
FROM (VALUES
    ('Clean Code',                              'Robert C. Martin', '9780132350884', 41.99, 25),
    ('Clean Architecture',                      'Robert C. Martin', '9780134494166', 38.50, 18),
    ('Domain-Driven Design',                    'Eric Evans',       '9780321125217', 57.75, 12),
    ('Refactoring',                             'Martin Fowler',    '9780134757599', 47.25, 15),
    ('Patterns of Enterprise Application Arch', 'Martin Fowler',    '9780321127426', 54.00, 8),
    ('Building Microservices',                  'Sam Newman',       '9781492034025', 44.90, 20),
    ('Monolith to Microservices',               'Sam Newman',       '9781492047841', 39.99, 10)
) AS v(title, author_name, isbn, price, stock)
JOIN author a ON a.name = v.author_name
ON CONFLICT (isbn) DO NOTHING;
