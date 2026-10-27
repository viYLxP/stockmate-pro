-- Online shop: customer accounts, published products, online orders with delivery details, and payments.
-- المتجر الإلكتروني: حسابات العملاء، والمنتجات المعروضة، والطلبات الإلكترونية مع بيانات التوصيل، والمدفوعات.

-- Customers sign in with their mobile number, stored in one form (9665XXXXXXXX) as the username.
-- العملاء يدخلون برقم الجوال، ويتخزن بشكل واحد (9665XXXXXXXX) كاسم مستخدم.
ALTER TABLE app_user ADD COLUMN phone VARCHAR(20);
ALTER TABLE app_user ADD COLUMN email VARCHAR(150);
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_role CHECK (role IN ('ADMIN', 'STAFF', 'CUSTOMER'));
ALTER TABLE app_user ADD CONSTRAINT ck_customer_phone CHECK (role <> 'CUSTOMER' OR phone ~ '^9665[0-9]{8}$');
CREATE UNIQUE INDEX ux_customer_phone ON app_user (phone) WHERE role = 'CUSTOMER';

-- A registered customer has one customer record, so their orders show in the office like any other customer's.
-- العميل المسجّل له سجل عميل واحد، فطلباته تظهر للمكتب مثل أي عميل ثاني.
ALTER TABLE customer ADD COLUMN user_id BIGINT UNIQUE REFERENCES app_user (id);

-- Only published products appear in the shop; the description is shown on the product page.
-- المنتجات المنشورة بس تظهر في المتجر؛ والوصف يظهر في صفحة المنتج.
ALTER TABLE product ADD COLUMN published BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE product ADD COLUMN description VARCHAR(1000);
-- Arabic name and description for the shop; the English ones are used when they are empty.
-- الاسم والوصف بالعربي للمتجر؛ ولو فاضيين نستخدم الإنجليزي.
ALTER TABLE product ADD COLUMN name_ar VARCHAR(150);
ALTER TABLE product ADD COLUMN description_ar VARCHAR(1000);
CREATE INDEX idx_product_published ON product (category_id) WHERE published AND active;
ALTER TABLE category ADD COLUMN name_ar VARCHAR(100);

ALTER TABLE sales_order ADD COLUMN channel VARCHAR(10) NOT NULL DEFAULT 'OFFICE'
    CHECK (channel IN ('OFFICE', 'ONLINE'));
ALTER TABLE sales_order ADD COLUMN payment_status VARCHAR(10) NOT NULL DEFAULT 'UNPAID'
    CHECK (payment_status IN ('UNPAID', 'PAID', 'REFUNDED'));
-- What the customer picked at checkout (CASH = cash on delivery). / اللي اختاره العميل وقت الطلب (CASH = الدفع عند الاستلام).
ALTER TABLE sales_order ADD COLUMN payment_choice VARCHAR(20)
    CHECK (payment_choice IN ('CARD', 'APPLE_PAY', 'TABBY', 'TAMARA', 'CASH'));
-- Delivery progress of an online order (NULL for office orders). / مراحل توصيل الطلب الإلكتروني (NULL لطلبات المكتب).
ALTER TABLE sales_order ADD COLUMN fulfilment VARCHAR(20)
    CHECK (fulfilment IN ('NEW', 'SHIPPED', 'DELIVERED'));
ALTER TABLE sales_order ADD COLUMN ship_name VARCHAR(120);
ALTER TABLE sales_order ADD COLUMN ship_phone VARCHAR(20);
ALTER TABLE sales_order ADD COLUMN ship_email VARCHAR(150);
ALTER TABLE sales_order ADD COLUMN ship_city VARCHAR(20);
ALTER TABLE sales_order ADD COLUMN ship_district VARCHAR(100);
ALTER TABLE sales_order ADD COLUMN ship_address VARCHAR(300);
-- An order paid online holds its stock until this time; then, if still unpaid, it is cancelled.
-- الطلب المدفوع أونلاين يحجز مخزونه لين هالوقت؛ وبعدها لو ما اندفع ينلغي.
ALTER TABLE sales_order ADD COLUMN pay_by TIMESTAMPTZ;
ALTER TABLE sales_order ADD COLUMN shipped_at TIMESTAMPTZ;
ALTER TABLE sales_order ADD COLUMN delivered_at TIMESTAMPTZ;
ALTER TABLE sales_order ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE sales_order ADD COLUMN cancel_reason VARCHAR(255);
-- Optimistic locking: payment, cancellation and the payment timeout never act on an old copy of the order.
-- قفل متفائل: الدفع والإلغاء وانتهاء المهلة ما يشتغلون أبدًا على نسخة قديمة من الطلب.
ALTER TABLE sales_order ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sales_order ADD CONSTRAINT ck_online_order CHECK (
    channel = 'OFFICE' OR (fulfilment IS NOT NULL AND ship_name IS NOT NULL AND ship_phone ~ '^9665[0-9]{8}$'
                           AND ship_city IS NOT NULL AND ship_district IS NOT NULL AND payment_choice IS NOT NULL));
CREATE INDEX idx_sales_order_pay_by ON sales_order (pay_by) WHERE pay_by IS NOT NULL;
CREATE INDEX idx_sales_order_ship_phone ON sales_order (ship_phone);

CREATE TABLE payment (
    id             UUID PRIMARY KEY,
    order_id       BIGINT         NOT NULL REFERENCES sales_order (id),
    method         VARCHAR(20)    NOT NULL CHECK (method IN ('CARD', 'APPLE_PAY', 'TABBY', 'TAMARA', 'CASH')),
    provider       VARCHAR(20)    NOT NULL
        CHECK (provider IN ('SANDBOX', 'MOYASAR', 'STRIPE', 'TABBY', 'TAMARA', 'CASH')),
    provider_ref   VARCHAR(200),
    checkout_url   VARCHAR(1000),
    amount         NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency       VARCHAR(3)     NOT NULL DEFAULT 'SAR',
    status         VARCHAR(20)    NOT NULL CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'REFUNDED')),
    failure_reason VARCHAR(255),
    card_brand     VARCHAR(20),
    card_last4     VARCHAR(4),
    recorded_by    VARCHAR(50),
    created_at     TIMESTAMPTZ    NOT NULL,
    paid_at        TIMESTAMPTZ,
    refunded_at    TIMESTAMPTZ,
    CONSTRAINT ck_payment_paid CHECK (status NOT IN ('PAID', 'REFUNDED') OR paid_at IS NOT NULL)
);
CREATE INDEX idx_payment_order ON payment (order_id);
-- At most one successful payment per order, even if two gateway callbacks race.
-- دفعة ناجحة وحدة بالكثير لكل طلب، حتى لو تسابق ردّين من البوابة.
CREATE UNIQUE INDEX ux_payment_one_paid ON payment (order_id) WHERE status = 'PAID';
