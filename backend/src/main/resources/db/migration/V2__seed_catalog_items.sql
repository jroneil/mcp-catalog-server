-- Local reference catalog. Fixed IDs and timestamps make demonstrations repeatable.
INSERT INTO public.catalog_item
    (id, sku, name, type, description, price, active, created_at, updated_at)
VALUES
    (1, 'PRD-101', 'Ergonomic Wireless Mouse', 'PRODUCT', 'Wireless mouse with adjustable sensitivity for office workstations.', 39.95, TRUE, '2026-01-15T09:00:00Z', '2026-01-15T09:00:00Z'),
    (2, 'PRD-102', 'USB-C Docking Station', 'PRODUCT', 'Desktop dock connecting dual monitors, wired networking and USB peripherals.', 189.00, TRUE, '2026-01-15T09:00:00Z', '2026-01-15T09:00:00Z'),
    (3, 'PRD-103', 'Adjustable Monitor Arm', 'PRODUCT', 'Clamp-mounted monitor support for shared desks and ergonomic workspaces.', 79.50, TRUE, '2026-01-15T09:00:00Z', '2026-01-15T09:00:00Z'),
    (4, 'PRD-104', 'Business Laptop 14 Inch', 'PRODUCT', 'Portable business laptop with 16 GB memory and encrypted solid-state storage.', 1249.00, TRUE, '2026-01-15T09:00:00Z', '2026-01-15T09:00:00Z'),
    (5, 'PRD-105', 'Conference Speakerphone', 'PRODUCT', 'USB speakerphone with echo cancellation for small meeting rooms.', 199.00, TRUE, '2026-01-15T09:00:00Z', '2026-01-15T09:00:00Z'),
    (6, 'PRD-106', 'Network Patch Cable Pack', 'PRODUCT', 'Five category 6 patch cables for office network cabinets.', 24.00, TRUE, '2026-01-15T09:00:00Z', '2026-01-15T09:00:00Z'),
    (7, 'PRD-107', 'Desktop Label Printer', 'PRODUCT', 'Thermal label printer for shipping desks and equipment identification.', 249.00, TRUE, '2026-01-15T09:00:00Z', '2026-01-15T09:00:00Z'),
    (8, 'PRD-108', 'Standing Desk Converter', 'PRODUCT', 'Height-adjustable desktop platform supporting a monitor and keyboard.', 329.00, TRUE, '2026-01-15T09:00:00Z', '2026-01-15T09:00:00Z'),
    (9, 'PRD-109', 'Document Scanner', 'PRODUCT', 'Duplex document scanner for digitizing invoices and signed agreements.', 449.00, TRUE, '2026-01-15T09:00:00Z', '2026-01-15T09:00:00Z'),
    (10, 'PRD-110', 'Legacy VGA Adapter', 'PRODUCT', 'Retired adapter for connecting older projectors to office laptops.', 19.95, FALSE, '2026-01-15T09:00:00Z', '2026-02-01T12:00:00Z'),
    (11, 'PRD-111', 'Desk Telephone Basic', 'PRODUCT', 'Discontinued wired desk phone retained for historical purchasing records.', 69.00, FALSE, '2026-01-15T09:00:00Z', '2026-02-01T12:00:00Z'),
    (12, 'PRD-112', 'Meeting Room Display 55 Inch', 'PRODUCT', 'Previous-generation commercial display for meeting rooms and signage.', 799.00, FALSE, '2026-01-15T09:00:00Z', '2026-02-01T12:00:00Z'),
    (13, 'SVC-101', 'Workstation Setup', 'SERVICE', 'Configure one employee workstation, install approved software and verify access.', 149.00, TRUE, '2026-01-15T09:00:00Z', '2026-01-15T09:00:00Z'),
    (14, 'SVC-102', 'Remote Troubleshooting Session', 'SERVICE', 'One hour of remote assistance for workstation or application issues.', 85.00, TRUE, '2026-01-15T09:00:00Z', '2026-01-15T09:00:00Z'),
    (15, 'SVC-103', 'Device Recycling Collection', 'SERVICE', 'Collect a small batch of retired office devices for certified recycling.', 45.00, TRUE, '2026-01-15T09:00:00Z', '2026-01-15T09:00:00Z'),
    (16, 'SVC-104', 'Network Health Assessment', 'SERVICE', 'Review office network configuration and provide a prioritized findings report.', 199.00, TRUE, '2026-01-15T09:00:00Z', '2026-01-15T09:00:00Z'),
    (17, 'SVC-105', 'Security Awareness Workshop', 'SERVICE', 'Instructor-led workshop on phishing, passwords and safe information handling.', 450.00, TRUE, '2026-01-15T09:00:00Z', '2026-01-15T09:00:00Z'),
    (18, 'SVC-106', 'Backup Recovery Exercise', 'SERVICE', 'Restore a sample business dataset and document recovery steps and timings.', 750.00, TRUE, '2026-01-15T09:00:00Z', '2026-01-15T09:00:00Z'),
    (19, 'SVC-107', 'Printer Installation', 'SERVICE', 'Install one network printer and configure employee print queues.', 120.00, TRUE, '2026-01-15T09:00:00Z', '2026-01-15T09:00:00Z'),
    (20, 'SVC-108', 'Initial Technology Consultation', 'SERVICE', 'Introductory discussion to identify business technology needs and next steps.', 0.00, TRUE, '2026-01-15T09:00:00Z', '2026-01-15T09:00:00Z'),
    (21, 'SVC-109', 'Office Relocation Planning', 'SERVICE', 'Prepare a technology relocation checklist and equipment transition schedule.', 1200.00, TRUE, '2026-01-15T09:00:00Z', '2026-01-15T09:00:00Z'),
    (22, 'SVC-110', 'Legacy Email Account Setup', 'SERVICE', 'Retired setup service for an email platform no longer offered to new customers.', 35.00, FALSE, '2026-01-15T09:00:00Z', '2026-02-01T12:00:00Z'),
    (23, 'SVC-111', 'On-site Desktop Tune-up', 'SERVICE', 'Retired desktop maintenance visit superseded by remote support packages.', 175.00, FALSE, '2026-01-15T09:00:00Z', '2026-02-01T12:00:00Z'),
    (24, 'SVC-112', 'Server Rack Commissioning', 'SERVICE', 'Retired package for rack installation, cable labeling and acceptance checks.', 950.00, FALSE, '2026-01-15T09:00:00Z', '2026-02-01T12:00:00Z');

ALTER TABLE public.catalog_item ALTER COLUMN id RESTART WITH 25;
