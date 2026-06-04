-- ===================================================================
-- VibeCart Ecommerce Module Restructuring: SPU/SKU Pattern
-- Version: V20260528133200__restructure_ecommerce_spu_sku.sql
-- Description: Drop old flat product model and recreate with
--   Category → Product (SPU) → ProductVariant (SKU) → Inventory
--   Also adds product_images, inventory_histories, and restructured orders.
-- ===================================================================

-- ============================================================
-- PHASE 1: DROP OLD TABLES (in correct FK dependency order)
-- ============================================================

DROP TABLE IF EXISTS post_products CASCADE;
DROP TABLE IF EXISTS click_events CASCADE;
DROP TABLE IF EXISTS short_links CASCADE;
DROP TABLE IF EXISTS payments CASCADE;
DROP TABLE IF EXISTS order_items CASCADE;
DROP TABLE IF EXISTS orders CASCADE;
DROP TABLE IF EXISTS inventories CASCADE;
DROP TABLE IF EXISTS products CASCADE;


-- ============================================================
-- PHASE 2: CREATE NEW ECOMMERCE TABLES
-- ============================================================

-- 2.1 Categories (hierarchical)
CREATE TABLE categories (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(120) UNIQUE NOT NULL,
    parent_id VARCHAR(36) REFERENCES categories(id),
    sort_order INT DEFAULT 0,
    deleted BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2.2 Products (SPU - Standard Product Unit)
CREATE TABLE products (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category_id VARCHAR(36) NOT NULL REFERENCES categories(id),
    creator_id VARCHAR(36) NOT NULL REFERENCES users(id),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    deleted BOOLEAN DEFAULT FALSE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50) DEFAULT 'system',
    updated_by VARCHAR(50) DEFAULT 'system'
);

-- 2.3 Product Images
CREATE TABLE product_images (
    id VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(36) NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    image_url VARCHAR(500) NOT NULL,
    is_thumbnail BOOLEAN DEFAULT FALSE,
    sort_order INT DEFAULT 0
);

-- 2.4 Product Variants (SKU - Stock Keeping Unit)
CREATE TABLE product_variants (
    id VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(36) NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sku_code VARCHAR(50) UNIQUE NOT NULL,
    variant_name VARCHAR(255) NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    discount_price DECIMAL(12,2) DEFAULT 0.00,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    deleted BOOLEAN DEFAULT FALSE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50) DEFAULT 'system',
    updated_by VARCHAR(50) DEFAULT 'system'
);

-- 2.5 Inventories (per variant)
CREATE TABLE inventories (
    id VARCHAR(36) PRIMARY KEY,
    variant_id VARCHAR(36) UNIQUE NOT NULL REFERENCES product_variants(id),
    quantity INT NOT NULL DEFAULT 0,
    reserved_quantity INT NOT NULL DEFAULT 0,
    deleted BOOLEAN DEFAULT FALSE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50) DEFAULT 'system',
    updated_by VARCHAR(50) DEFAULT 'system'
);

-- 2.6 Inventory Histories (audit log)
CREATE TABLE inventory_histories (
    id VARCHAR(36) PRIMARY KEY,
    inventory_id VARCHAR(36) NOT NULL REFERENCES inventories(id),
    transaction_type VARCHAR(20) NOT NULL,
    quantity_changed INT NOT NULL,
    reason VARCHAR(500),
    created_by VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2.7 Orders (restructured)
CREATE TABLE orders (
    id VARCHAR(36) PRIMARY KEY,
    order_code VARCHAR(50) UNIQUE NOT NULL,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id),
    creator_id VARCHAR(36) NOT NULL REFERENCES users(id),
    recipient_name VARCHAR(100) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    shipping_address TEXT NOT NULL,
    customer_note VARCHAR(255),
    total_amount DECIMAL(12,2) NOT NULL,
    discount_amount DECIMAL(12,2) DEFAULT 0.00,
    final_amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    payment_link_id VARCHAR(255),
    deleted BOOLEAN DEFAULT FALSE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50) DEFAULT 'system',
    updated_by VARCHAR(50) DEFAULT 'system'
);

-- 2.8 Order Items (restructured)
CREATE TABLE order_items (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    variant_id VARCHAR(36) NOT NULL REFERENCES product_variants(id),
    product_name VARCHAR(255) NOT NULL,
    variant_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    discount_price DECIMAL(12,2) DEFAULT 0.00
);


-- ============================================================
-- PHASE 3: RECREATE CROSS-MODULE TABLES
-- ============================================================

-- 3.1 Post-Product link (Social → Ecommerce)
CREATE TABLE post_products (
    post_id VARCHAR(36) NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    product_id VARCHAR(36) NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    PRIMARY KEY (post_id, product_id)
);

-- 3.2 Short Links (Affiliate → Ecommerce)
CREATE TABLE short_links (
    id VARCHAR(36) PRIMARY KEY,
    short_code VARCHAR(10) UNIQUE NOT NULL,
    original_url TEXT NOT NULL,
    product_id VARCHAR(36) NOT NULL REFERENCES products(id),
    creator_id VARCHAR(36) NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50) DEFAULT 'system',
    updated_by VARCHAR(50) DEFAULT 'system',
    deleted BOOLEAN DEFAULT FALSE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- 3.3 Click Events (Affiliate analytics)
CREATE TABLE click_events (
    id VARCHAR(36) PRIMARY KEY,
    short_link_id VARCHAR(36) NOT NULL REFERENCES short_links(id) ON DELETE CASCADE,
    click_time TIMESTAMP WITH TIME ZONE NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    device_type VARCHAR(50),
    browser VARCHAR(50),
    country VARCHAR(50),
    commission_earned DECIMAL(12, 2) DEFAULT 0.00
);


-- ============================================================
-- PHASE 4: CREATE INDEXES
-- ============================================================

-- Categories
CREATE INDEX idx_categories_slug ON categories(slug);
CREATE INDEX idx_categories_parent ON categories(parent_id);

-- Products
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_creator ON products(creator_id);
CREATE INDEX idx_products_status ON products(status);
CREATE INDEX idx_products_name ON products(name);

-- Product Images
CREATE INDEX idx_product_images_product ON product_images(product_id);

-- Product Variants
CREATE INDEX idx_product_variants_product ON product_variants(product_id);
CREATE INDEX idx_product_variants_sku ON product_variants(sku_code);
CREATE INDEX idx_product_variants_status ON product_variants(status);

-- Inventories
CREATE INDEX idx_inventories_variant ON inventories(variant_id);

-- Inventory Histories
CREATE INDEX idx_inventory_histories_inventory ON inventory_histories(inventory_id);
CREATE INDEX idx_inventory_histories_created_at ON inventory_histories(created_at DESC);

-- Orders
CREATE INDEX idx_orders_user ON orders(user_id);
CREATE INDEX idx_orders_creator ON orders(creator_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_code ON orders(order_code);

-- Order Items
CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_order_items_variant ON order_items(variant_id);

-- Short Links (recreated)
CREATE INDEX idx_short_links_code ON short_links(short_code);
CREATE INDEX idx_short_links_creator ON short_links(creator_id);

-- Click Events (recreated)
CREATE INDEX idx_click_events_short_link ON click_events(short_link_id);
CREATE INDEX idx_click_events_time ON click_events(click_time DESC);
