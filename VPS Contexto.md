# 📋 DOCUMENTACIÓN COMPLETA VPS - Inventario API

**Fecha de última actualización:** 03 de Enero, 2026  
**Dominio:** `inventariopy.ddns.net`  
**Sistema Operativo:** Ubuntu 24.04 LTS  
**Usuario principal:** `administrator`

---

## 🌐 INFORMACIÓN GENERAL DEL SERVIDOR

### **Acceso al servidor**
```bash
# SSH
ssh administrator@[IP_DEL_VPS]

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
    ├── index.js                       # Punto de entrada
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

# JWT Secret
JWT_SECRET=[tu_secreto_jwt]

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

-- Ver usuarios
SELECT User, Host FROM mysql.user;

-- Ver permisos
SHOW GRANTS FOR 'inventario_user'@'localhost';
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

## 🌐 ENDPOINTS DISPONIBLES

### **API Node.js (Inventario)**

Base URL: `https://inventariopy.ddns.net/api/`

| Endpoint | Método | Descripción | Auth |
|----------|--------|-------------|------|
| `/api/health` | GET | Health check de la API | No |
| `/api/auth/login` | POST | Login de usuarios | No |
| `/api/auth/register` | POST | Registro de usuarios | No |
| `/api/*` | * | Otros endpoints de tu API | Depende |

**Ejemplo de uso:**
```bash
# Health check
curl https://inventariopy.ddns.net/api/health

# Login
curl -X POST https://inventariopy.ddns.net/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
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
Contraseña: Inventario2026!  # ⚠️ CAMBIAR DESPUÉS
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
- Rate limiting en Nginx (10 req/s)
- Puertos internos (3000, 3306) cerrados al público
- Bloqueo de user-agents maliciosos (l9scan, zgrab, etc.)
- MySQL solo acepta conexiones localhost

### ⚠️ **Pendiente (RECOMENDADO):**
- Cambiar contraseña de MySQL después de compartir
- Configurar autenticación SSH por llave (deshabilitar password)
- Implementar rate limiting por IP en endpoints sensibles
- Configurar backups automáticos de base de datos
- Implementar sistema de logs centralizado (opcional)
- Cambiar password default del usuario "admin" en la API

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
```

---

**🎯 Servidor configurado y funcionando correctamente**  
**Última verificación:** 03 de Enero, 2026

---

**Resumen de la arquitectura:**
- **Frontend (App Android)** → **HTTPS** → **Nginx (443)** → **Node.js API (3000)** → **MySQL (3306)**
- Todo protegido por SSL, Firewall y Rate Limiting
- Dominio dinámico con No-IP
- Procesos gestionados por PM2 con auto-restart