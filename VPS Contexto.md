# 📋 DOCUMENTACIÓN COMPLETA VPS - Inventario API

**Fecha de última actualización:** 03 de Enero, 2026  
**Dominio:** `inventariopy.ddns.net`  
**Sistema Operativo:** Ubuntu 24.04 LTS  
**Usuario principal:** `administrator`  
**Estado:** ✅ Funcionando correctamente

---

## 🌐 INFORMACIÓN GENERAL DEL SERVIDOR

### **Acceso al servidor**
```bash
# SSH
ssh administrator@155.117.45.228

# Usuario: administrator
# Se recomienda usar autenticación por llave SSH
```

### **Dominio y DNS**
- **Proveedor:** No-IP (servicio gratuito)
- **Dominio principal:** `inventariopy.ddns.net`
- **Tipo:** DNS dinámico (DDNS)
- **Renovación:** Requiere confirmación mensual por email

---

## 🗂️ ESTRUCTURA DE DIRECTORIOS

```
/var/www/
└── inventario-api/                    # API Node.js principal
    ├── index.js                       # Punto de entrada (código completo)
    ├── .env                           # Variables de entorno
    ├── package.json                   # Dependencias Node.js
    └── node_modules/                  # Librerías instaladas

/etc/nginx/
├── nginx.conf                         # Configuración principal
└── sites-available/
    └── inventario-api                 # Config del sitio
└── sites-enabled/
    └── inventario-api -> ../sites-available/inventario-api

/home/administrator/.pm2/
├── logs/
│   ├── inventario-api-out.log        # Logs de salida
│   └── inventario-api-error.log      # Logs de error
└── pids/
```

---

## 🔧 SERVICIOS CONFIGURADOS

### **1. Node.js API (inventario-api)**

**Tecnología:** Node.js + Express  
**Puerto interno:** `3000`  
**Gestor de procesos:** PM2  
**Estado:** ✅ Activo  

**Ubicación:** `/var/www/inventario-api/`

**Archivo .env:**
```env
# Database Configuration
DB_HOST=localhost
DB_PORT=3306
DB_NAME=inventario_db
DB_USER=inventario_user
DB_PASSWORD=Inventario2026!

# Server Configuration
PORT=3000
NODE_ENV=production

# JWT Configuration (⚠️ CRÍTICO)
JWT_SECRET=inventario_py_secret_key_2026_production_super_secure
JWT_EXPIRES_IN=7d
JWT_REFRESH_SECRET=inventario_py_refresh_secret_key_2026_ultra_secure
JWT_REFRESH_EXPIRES_IN=30d

# CORS
ALLOWED_ORIGINS=*
```

**Comandos PM2:**
```bash
# Ver estado
pm2 status

# Ver logs
pm2 logs inventario-api
pm2 logs inventario-api --lines 100

# Reiniciar
pm2 restart inventario-api

# Detener
pm2 stop inventario-api

# Eliminar del PM2
pm2 delete inventario-api

# Iniciar manualmente
cd /var/www/inventario-api
pm2 start index.js --name "inventario-api"
pm2 save
```

---

### **2. MySQL Database**

**Versión:** MySQL 8.0+  
**Puerto:** `3306` (solo localhost)  
**Estado:** ✅ Activo

**Base de datos:**
- **Nombre:** `inventario_db`
- **Usuario:** `inventario_user`
- **Contraseña:** `Inventario2026!`
- **Permisos:** ALL PRIVILEGES en inventario_db.*

**Tablas creadas:**
- `users` - Usuarios del sistema
- `categories` - Categorías de productos
- `suppliers` - Proveedores
- `products` - Productos del inventario
- `product_variants` - Variantes de productos (tallas, colores, etc.)
- `sales` - Ventas realizadas
- `sale_items` - Items de cada venta
- `stock_movements` - Historial de movimientos de stock

**Acceso:**
```bash
# Conectar como usuario de la app
mysql -u inventario_user -p inventario_db

# Conectar como root
sudo mysql
# o
sudo mysql -u root -p
```

**Comandos útiles:**
```sql
-- Ver bases de datos
SHOW DATABASES;

-- Usar base de datos
USE inventario_db;

-- Ver tablas
SHOW TABLES;

-- Ver estructura de una tabla
DESCRIBE products;

-- Ver usuarios
SELECT User, Host FROM mysql.user;

-- Ver permisos
SHOW GRANTS FOR 'inventario_user'@'localhost';
```

**Usuario administrador por defecto:**
```
Username: admin
Password: admin123
Role: OWNER
Email: admin@inventario.py
```

---

### **3. Nginx (Proxy Reverso y Web Server)**

**Versión:** Nginx 1.24+  
**Puertos:** 80 (HTTP), 443 (HTTPS)  
**Estado:** ✅ Activo

**Configuración actual:** `/etc/nginx/sites-available/inventario-api`

```nginx
# Límite de requests
limit_req_zone $binary_remote_addr zone=api_limit:10m rate=10r/s;

server {
    listen 80;
    server_name inventariopy.ddns.net;

    # Protección contra bots maliciosos
    if ($http_user_agent ~* (l9scan|zgrab|censys|shodan)) {
        return 403;
    }

    # Aplicar límite de requests
    limit_req zone=api_limit burst=20 nodelay;
    
    client_max_body_size 10M;

    # API Node.js (puerto 3000)
    location /api/ {
        proxy_pass http://localhost:3000/api/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
        
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # Health check
    location /health {
        proxy_pass http://localhost:3000/health;
    }

    listen 443 ssl; # managed by Certbot
    ssl_certificate /etc/letsencrypt/live/inventariopy.ddns.net/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/inventariopy.ddns.net/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;
}

server {
    if ($host = inventariopy.ddns.net) {
        return 301 https://$host$request_uri;
    }

    listen 80;
    server_name inventariopy.ddns.net;
    return 404;
}
```

**Comandos Nginx:**
```bash
# Verificar configuración
sudo nginx -t

# Reiniciar
sudo systemctl restart nginx

# Recargar (sin downtime)
sudo systemctl reload nginx

# Ver estado
sudo systemctl status nginx

# Ver logs
sudo tail -f /var/log/nginx/access.log
sudo tail -f /var/log/nginx/error.log
```

---

### **4. Let's Encrypt SSL/TLS**

**Estado:** ✅ Activo  
**Certificado válido para:** `inventariopy.ddns.net`  
**Renovación:** Automática cada 90 días  

**Ubicación certificados:**
```
/etc/letsencrypt/live/inventariopy.ddns.net/
├── fullchain.pem       # Certificado completo
├── privkey.pem         # Llave privada
├── cert.pem            # Certificado del servidor
└── chain.pem           # Cadena de certificación
```

**Comandos Certbot:**
```bash
# Renovar manualmente
sudo certbot renew

# Probar renovación
sudo certbot renew --dry-run

# Ver certificados instalados
sudo certbot certificates

# Agregar nuevo dominio
sudo certbot --nginx -d nuevo-dominio.com
```

---

### **5. UFW Firewall**

**Estado:** ✅ Activo

**Reglas configuradas:**
```bash
# Ver reglas
sudo ufw status verbose

# Salida esperada:
# Status: active
# 
# To                         Action      From
# --                         ------      ----
# 22/tcp (OpenSSH)          ALLOW IN    Anywhere
# 80,443/tcp (Nginx Full)   ALLOW IN    Anywhere
```

**Comandos UFW:**
```bash
# Ver estado
sudo ufw status numbered

# Permitir puerto
sudo ufw allow [PUERTO]/tcp

# Denegar puerto
sudo ufw deny [PUERTO]/tcp

# Eliminar regla por número
sudo ufw delete [NUMERO]

# Deshabilitar (cuidado)
sudo ufw disable

# Habilitar
sudo ufw enable
```

---

### **6. Fail2Ban**

**Estado:** ✅ Activo  
**Jails configuradas:** sshd (protección SSH)

**Comandos:**
```bash
# Ver estado general
sudo fail2ban-client status

# Ver IPs baneadas en SSH
sudo fail2ban-client status sshd

# Desbanear IP
sudo fail2ban-client set sshd unbanip [IP_ADDRESS]

# Ver logs
sudo tail -f /var/log/fail2ban.log
```

---

## 🌐 API ENDPOINTS Y ESTRUCTURA DE RESPUESTAS

**Base URL:** `https://inventariopy.ddns.net/api/`

### **📌 Estructura General de Respuestas**

Todas las respuestas de la API siguen este formato estándar:

**Respuesta exitosa:**
```json
{
  "success": true,
  "message": "Mensaje descriptivo (opcional)",
  "data": { /* Datos de respuesta */ },
  "pagination": { /* Solo en endpoints paginados */ }
}
```

**Respuesta de error:**
```json
{
  "success": false,
  "message": "Descripción del error"
}
```

---

### **🔐 Autenticación**

#### **1. POST /api/auth/login**
Login de usuario y obtención de tokens JWT.

**Request:**
```json
{
  "username": "admin",      // Puede ser username o email
  "password": "admin123"
}
```

**Response exitoso (200 OK):**
```json
{
  "success": true,
  "message": "Login exitoso",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 604800000,    // 7 días en milisegundos
    "user": {
      "id": "admin-001",
      "username": "admin",
      "email": "admin@inventario.py",
      "fullName": "Administrador",
      "role": "OWNER",         // "OWNER" o "EMPLOYEE"
      "isActive": true,
      "profileImageUrl": null,  // URL de imagen de perfil
      "phoneNumber": null,
      "createdAt": 1767456359000,      // Timestamp en ms
      "updatedAt": 1767456359000,
      "lastLoginAt": 1767472582333
    }
  }
}
```

**Errores posibles:**
- `401 Unauthorized`: Credenciales inválidas
- `500 Internal Server Error`: Error del servidor

**Ejemplo de uso:**
```bash
curl -X POST https://inventariopy.ddns.net/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

---

#### **2. POST /api/auth/refresh**
Renovar token de acceso usando el refresh token.

**Request:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "token": "nuevo_token_jwt",
  "refreshToken": "nuevo_refresh_token",
  "expiresIn": 604800000
}
```

---

#### **3. GET /api/auth/me**
Obtener información del usuario actual (requiere autenticación).

**Headers:**
```
Authorization: Bearer {token}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "id": "admin-001",
    "username": "admin",
    "email": "admin@inventario.py",
    "fullName": "Administrador",
    "role": "OWNER",
    "isActive": true,
    "profileImageUrl": null,
    "phoneNumber": null,
    "createdAt": 1767456359000,
    "updatedAt": 1767456359000,
    "lastLoginAt": 1767472582333
  }
}
```

---

#### **4. POST /api/auth/logout**
Cerrar sesión (requiere autenticación).

**Response (200 OK):**
```json
{
  "success": true,
  "data": null
}
```

---

### **📦 Productos**

#### **1. GET /api/products**
Obtener lista de productos (requiere autenticación).

**Query Parameters:**
- `page` (optional): Número de página (default: 1)
- `limit` (optional): Items por página (default: 50)
- `category` (optional): ID de categoría para filtrar
- `search` (optional): Búsqueda por nombre, barcode o identifier
- `lowStock` (optional): "true" para ver solo productos con stock bajo

**Response (200 OK):**
```json
{
  "success": true,
  "data": [
    {
      "id": "prod-001",
      "name": "Coca Cola 2L",
      "description": "Bebida gaseosa",
      "barcode": "7891234567890",
      "identifier": "PROD-001",
      "imageUrl": "https://...",
      "categoryId": "cat-001",
      "totalStock": 50,
      "minStockAlert": 10,
      "isStockAlertEnabled": true,
      "salePrice": 15000,        // En centavos (Gs. 15,000)
      "purchasePrice": 12000,
      "supplierId": "sup-001",
      "supplierName": "Distribuidora ABC",
      "isActive": true,
      "createdAt": 1767456359000,
      "updatedAt": 1767456359000,
      "createdBy": "admin-001"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 50,
    "total": 150,
    "totalPages": 3
  }
}
```

---

#### **2. GET /api/products/:id**
Obtener producto por ID (incluye variantes).

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "id": "prod-001",
    "name": "Remera Nike",
    /* ... campos del producto ... */,
    "variants": [
      {
        "id": "var-001",
        "productId": "prod-001",
        "variantType": "size",
        "variantValue": "M",
        "additionalPrice": 0,
        "stock": 10,
        "barcode": "789123456001",
        "isActive": true
      }
    ]
  }
}
```

---

#### **3. GET /api/products/barcode/:barcode**
Buscar producto por código de barras.

**Response:** Igual que GET /api/products/:id

---

#### **4. POST /api/products**
Crear nuevo producto (requiere autenticación).

**Request:**
```json
{
  "name": "Producto Nuevo",
  "description": "Descripción del producto",
  "barcode": "7891234567890",
  "identifier": "PROD-123",    // Opcional, se genera automáticamente
  "imageUrl": "https://...",
  "categoryId": "cat-001",
  "totalStock": 100,
  "minStockAlert": 10,
  "salePrice": 50000,
  "purchasePrice": 35000,
  "supplierId": "sup-001",
  "supplierName": "Proveedor XYZ"
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "data": { /* producto creado */ }
}
```

---

#### **5. PUT /api/products/:id**
Actualizar producto existente.

**Request:** Campos a actualizar (todos opcionales)

**Response (200 OK):**
```json
{
  "success": true,
  "data": { /* producto actualizado */ }
}
```

---

#### **6. POST /api/products/:id/stock**
Actualizar stock de producto (ajuste manual).

**Request:**
```json
{
  "quantity": 10,              // Positivo = agregar, Negativo = quitar
  "movementType": "MANUAL_ADJUSTMENT",
  "reason": "Conteo físico",
  "variantId": null            // Opcional, para variantes
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": { /* producto actualizado */ }
}
```

---

### **💰 Ventas**

#### **1. GET /api/sales**
Obtener lista de ventas.

**Query Parameters:**
- `page`, `limit`: Paginación
- `startDate`, `endDate`: Filtrar por rango de fechas (timestamps en ms)
- `status`: Filtrar por estado ("COMPLETED", "CANCELLED")

**Response (200 OK):**
```json
{
  "success": true,
  "data": [
    {
      "id": "sale-001",
      "saleNumber": "V1767472582333",
      "subtotal": 100000,
      "totalDiscount": 5000,
      "total": 95000,
      "paymentMethod": "CASH",    // CASH, CARD, TRANSFER
      "amountReceived": 100000,
      "change": 5000,
      "customerName": "Juan Pérez",
      "notes": "Cliente frecuente",
      "status": "COMPLETED",
      "soldAt": 1767472582333,
      "sellerId": "admin-001",
      "sellerName": "Administrador",
      "cancellationReason": null,
      "cancelledAt": null
    }
  ],
  "pagination": { /* ... */ }
}
```

---

#### **2. POST /api/sales**
Crear nueva venta (actualiza stock automáticamente).

**Request:**
```json
{
  "items": [
    {
      "productId": "prod-001",
      "variantId": null,
      "productName": "Coca Cola 2L",
      "variantDescription": null,
      "quantity": 2,
      "unitPrice": 15000,
      "discount": 0,
      "subtotal": 30000
    }
  ],
  "paymentMethod": "CASH",
  "subtotal": 30000,
  "totalDiscount": 0,
  "total": 30000,
  "amountReceived": 50000,
  "change": 20000,
  "customerName": "Cliente X",
  "notes": "Venta de mostrador"
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "data": { /* venta creada */ }
}
```

---

#### **3. POST /api/sales/:id/cancel**
Cancelar una venta (restaura stock).

**Request:**
```json
{
  "reason": "Error en el pedido"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": { /* venta cancelada */ }
}
```

---

### **📊 Estadísticas**

#### **GET /api/stats/dashboard**
Obtener estadísticas del dashboard.

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "todaySales": 500000,           // Total vendido hoy
    "todayTransactions": 15,         // Cantidad de ventas hoy
    "monthSales": 12000000,          // Total vendido este mes
    "monthTransactions": 380,
    "totalProducts": 250,            // Total de productos activos
    "lowStockProducts": 12,          // Productos con stock bajo
    "outOfStockProducts": 3          // Productos sin stock
  }
}
```

---

### **📂 Categorías y Proveedores**

#### **GET /api/categories**
Obtener todas las categorías activas.

**Response (200 OK):**
```json
{
  "success": true,
  "data": [
    {
      "id": "cat-001",
      "name": "Bebidas",
      "description": "Bebidas y refrescos",
      "parentId": null,
      "iconName": "drink",
      "colorHex": "#FF5733",
      "sortOrder": 0,
      "isActive": true,
      "createdAt": 1767456359000,
      "updatedAt": 1767456359000
    }
  ]
}
```

---

#### **GET /api/suppliers**
Obtener todos los proveedores activos.

**Response (200 OK):**
```json
{
  "success": true,
  "data": [
    {
      "id": "sup-001",
      "name": "Distribuidora ABC",
      "contactName": "Juan Ramírez",
      "phone": "0981123456",
      "email": "contacto@distri-abc.com",
      "address": "Av. Principal 123",
      "city": "Asunción",
      "notes": "Proveedor principal",
      "isActive": true,
      "createdAt": 1767456359000,
      "updatedAt": 1767456359000
    }
  ]
}
```

---

### **🔄 Sincronización**

#### **POST /api/sync**
Endpoint para sincronización con apps offline (en desarrollo).

**Request:**
```json
{
  "lastSyncTime": 1767456359000,
  "changes": []
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "serverTime": 1767472582333,
    "changes": []
  }
}
```

---

### **❤️ Health Check**

#### **GET /api/health**
Verificar que el servidor está funcionando (no requiere autenticación).

**Response (200 OK):**
```json
{
  "status": "ok",
  "timestamp": 1767472582333
}
```

---

## 🔌 PUERTOS UTILIZADOS

| Puerto | Servicio | Acceso | Estado |
|--------|----------|--------|--------|
| 22 | SSH | Público (protegido por Fail2Ban) | ✅ Abierto |
| 80 | HTTP | Público (redirige a 443) | ✅ Abierto |
| 443 | HTTPS | Público (Nginx con SSL) | ✅ Abierto |
| 3000 | Node.js API | Solo localhost | 🔒 Cerrado |
| 3306 | MySQL | Solo localhost | 🔒 Cerrado |

**Nota:** Los puertos 3000 y 3306 NO son accesibles desde internet directamente. Solo Nginx puede acceder al puerto 3000 internamente.

---

## 🔐 CREDENCIALES Y SEGURIDAD

### **MySQL**
```
Host: localhost
Port: 3306
Database: inventario_db
Usuario: inventario_user
Contraseña: Inventario2026!  # ⚠️ CAMBIAR EN PRODUCCIÓN
```

### **Usuario Admin de la API**
```
Username: admin
Password: admin123           # ⚠️ CAMBIAR EN PRODUCCIÓN
Email: admin@inventario.py
Role: OWNER
```

### **No-IP (DNS Dinámico)**
```
Dominio: inventariopy.ddns.net
Email: [tu_email]
Password: [tu_password_noip]
```

### **Usuario del VPS**
```
Usuario: administrator
SSH: Usar autenticación por llave (recomendado)
```

---

## 📊 COMANDOS DE MONITOREO

### **Estado general del servidor**
```bash
# Uso de CPU y memoria en tiempo real
htop
# o
top

# Uso de disco
df -h

# Memoria RAM
free -h

# Procesos de Node.js
ps aux | grep node

# Conexiones activas
netstat -tulpn | grep -E ":(80|443|3000|3306)"
# o
ss -tulpn | grep -E ":(80|443|3000|3306)"
```

### **Logs centralizados**
```bash
# PM2 - Ver logs
pm2 logs inventario-api
pm2 logs inventario-api --lines 100
pm2 logs inventario-api --err --lines 50

# Nginx - Access log
sudo tail -f /var/log/nginx/access.log

# Nginx - Error log
sudo tail -f /var/log/nginx/error.log

# MySQL - Error log
sudo tail -f /var/log/mysql/error.log

# Sistema - Syslog
sudo tail -f /var/log/syslog

# Fail2Ban
sudo tail -f /var/log/fail2ban.log
```

### **Verificación de servicios**
```bash
# Estado de todos los servicios
sudo systemctl status nginx
sudo systemctl status mysql
sudo systemctl status fail2ban

# PM2
pm2 status
pm2 monit  # Monitor en tiempo real
```

---

## 🚀 COMANDOS DE DESPLIEGUE/ACTUALIZACIÓN

### **Actualizar API Node.js**
```bash
# 1. Ir al directorio
cd /var/www/inventario-api

# 2. Backup (opcional)
cp -r /var/www/inventario-api /var/www/inventario-api.backup

# 3. Actualizar código (git pull, ftp, etc.)
# ...

# 4. Instalar dependencias
npm install --production

# 5. Reiniciar PM2
pm2 restart inventario-api

# 6. Verificar logs
pm2 logs inventario-api --lines 50
```

### **Actualizar configuración Nginx**
```bash
# 1. Editar configuración
sudo nano /etc/nginx/sites-available/inventario-api

# 2. Verificar sintaxis
sudo nginx -t

# 3. Recargar Nginx (sin downtime)
sudo systemctl reload nginx

# 4. O reiniciar completamente
sudo systemctl restart nginx
```

---

## 🛡️ SEGURIDAD IMPLEMENTADA

### ✅ **Configurado:**
- Firewall UFW activo (solo SSH, HTTP, HTTPS)
- Fail2Ban protegiendo SSH
- SSL/TLS con certificado Let's Encrypt
- Rate limiting en Nginx (10 req/s, burst 20)
- Puertos internos (3000, 3306) cerrados al público
- Bloqueo de user-agents maliciosos (l9scan, zgrab, etc.)
- MySQL solo acepta conexiones localhost
- JWT para autenticación de API
- Tokens con expiración (7 días access, 30 días refresh)
- Password hashing con bcrypt (rounds: 10)

### ⚠️ **Pendiente (RECOMENDADO):**
- Cambiar contraseña de MySQL en producción
- Cambiar password del usuario admin
- Configurar autenticación SSH por llave (deshabilitar password)
- Implementar rate limiting por IP en endpoints sensibles
- Configurar backups automáticos de base de datos
- Implementar sistema de logs centralizado (opcional)
- Rotar JWT_SECRET periódicamente

---

## 🔄 RUTINAS DE MANTENIMIENTO

### **Diario:**
```bash
# Verificar estado de servicios
pm2 status
sudo systemctl status nginx mysql fail2ban

# Revisar logs de errores
pm2 logs inventario-api --err --lines 20
```

### **Semanal:**
```bash
# Actualizar paquetes del sistema
sudo apt update && sudo apt upgrade -y

# Verificar espacio en disco
df -h

# Revisar IPs baneadas
sudo fail2ban-client status sshd
```

### **Mensual:**
```bash
# Confirmar dominio No-IP (email automático)
# Verificar renovación de certificado SSL
sudo certbot certificates

# Backup de base de datos
mysqldump -u inventario_user -p inventario_db > backup_$(date +%Y%m%d).sql

# Limpiar logs antiguos
pm2 flush
```

---

## 🆘 TROUBLESHOOTING COMÚN

### **La API no responde**
```bash
# 1. Verificar que PM2 está corriendo
pm2 status

# 2. Ver logs de errores
pm2 logs inventario-api --err --lines 50

# 3. Verificar conexión a MySQL
mysql -u inventario_user -p inventario_db

# 4. Reiniciar el servicio
pm2 restart inventario-api

# 5. Si todo falla, reiniciar desde cero
pm2 delete inventario-api
cd /var/www/inventario-api
pm2 start index.js --name "inventario-api"
pm2 save
```

### **Error de conexión a MySQL**
```bash
# 1. Verificar que MySQL está corriendo
sudo systemctl status mysql

# 2. Verificar credenciales en .env
cat /var/www/inventario-api/.env

# 3. Probar conexión manual
mysql -u inventario_user -p inventario_db

# 4. Recrear usuario si es necesario
sudo mysql
DROP USER IF EXISTS 'inventario_user'@'localhost';
CREATE USER 'inventario_user'@'localhost' IDENTIFIED WITH mysql_native_password BY 'Inventario2026!';
GRANT ALL PRIVILEGES ON inventario_db.* TO 'inventario_user'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

### **Problemas con contraseñas (bcrypt)**
```bash
# Generar nuevo hash para una contraseña
cd /var/www/inventario-api
node -e "
const bcrypt = require('bcryptjs');
bcrypt.hash('tu_password', 10, (err, hash) => {
    console.log('Hash:', hash);
});
"

# Actualizar en la base de datos
mysql -u inventario_user -p inventario_db
UPDATE users SET password_hash = 'HASH_GENERADO' WHERE username = 'admin';
EXIT;
```

### **Nginx da error 502 Bad Gateway**
```bash
# 1. Verificar que el backend está corriendo
curl http://localhost:3000/api/health

# 2. Ver logs de Nginx
sudo tail -f /var/log/nginx/error.log

# 3. Verificar configuración
sudo nginx -t

# 4. Reiniciar Nginx
sudo systemctl restart nginx
```

### **Certificado SSL expirado**
```bash
# Renovar manualmente
sudo certbot renew --force-renewal

# Reiniciar Nginx
sudo systemctl restart nginx

# Verificar
curl -I https://inventariopy.ddns.net
```

---

## 📝 NOTAS ADICIONALES

### **Escaneos automáticos en logs**
Es normal ver en los logs intentos de acceso de servicios como:
- `leakix.net` - Scanner de seguridad
- `shodan.io` - Motor de búsqueda IoT
- `censys` - Scanner de certificados
- Bots de China/Rusia

**Estos escaneos son automáticos y suceden en TODOS los servidores públicos.** Nginx y Fail2Ban los están bloqueando correctamente.

### **Renovación mensual No-IP**
No-IP gratuito requiere que **confirmes tu dominio cada 30 días** por email. Si no lo haces, el dominio se desactiva.

### **Backups recomendados**
```bash
# Backup completo semanal
mkdir -p /home/administrator/backups

# Base de datos
mysqldump -u inventario_user -p inventario_db > /home/administrator/backups/db_$(date +%Y%m%d).sql

# Código
tar -czf /home/administrator/backups/api_$(date +%Y%m%d).tar.gz /var/www/inventario-api

# Configuraciones
tar -czf /home/administrator/backups/nginx_$(date +%Y%m%d).tar.gz /etc/nginx/sites-available/
```

---

## ✅ CHECKLIST DE VERIFICACIÓN RÁPIDA

```bash
# ¿Todo funciona?
curl https://inventariopy.ddns.net/api/health        # Debería responder 200 OK
pm2 status                                            # Servicio "online"
sudo systemctl status nginx mysql fail2ban            # Todos "active (running)"
sudo ufw status                                       # "Status: active"

# Login funciona?
curl -X POST https://inventariopy.ddns.net/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

---

**🎯 Servidor configurado y funcionando correctamente**  
**Última verificación:** 03 de Enero, 2026  
**Estado:** ✅ Todos los servicios operativos

---

**Arquitectura del sistema:**
```
[App Android] 
    ↓ HTTPS
[Internet]
    ↓ 
[Nginx :443 SSL]
    ↓ proxy_pass
[Node.js API :3000]
    ↓
[MySQL :3306]
```

**Flujo de autenticación:**
1. Cliente envía credentials → `/api/auth/login`
2. API valida con bcrypt contra MySQL
3. API genera JWT token (firma con JWT_SECRET)
4. Cliente guarda token
5. Cliente envía token en header: `Authorization: Bearer {token}`
6. API valida token en cada request protegido

---

¡Documentación completa actualizada! 🚀✨