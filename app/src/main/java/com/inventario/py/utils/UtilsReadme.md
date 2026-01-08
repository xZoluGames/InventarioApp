# 📁 DOCUMENTACIÓN - Carpeta `/utils`

> **Proyecto:** InventarioPy - Sistema de Inventario Android  
> **Paquete:** `com.inventario.py.utils`  
> **Última actualización:** Enero 2026

---

## 📋 ÍNDICE DE ARCHIVOS

| Archivo | Tipo | Descripción |
|---------|------|-------------|
| `SessionManager.kt` | Singleton (Hilt) | Gestión de sesión y autenticación del usuario |
| `AuthInterceptor.kt` | Interceptor OkHttp | Inyección automática de token JWT en peticiones |
| `CurrencyUtils.kt` | Object | Formateo de moneda en Guaraníes (Gs.) |
| `DateUtils.kt` | Object | Formateo y manipulación de fechas |
| `Utils.kt` | Múltiple | Constantes, extensiones de View, FileUtils, ExcelUtils, Validators, Generators |

---

## 🔐 SessionManager.kt

### **Propósito**
Gestiona toda la sesión del usuario: tokens JWT, información del usuario logueado, y preferencias de servidor. Usa `EncryptedSharedPreferences` para almacenamiento seguro.

### **Inyección de Dependencias**
```kotlin
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
)
```

### **Almacenamiento**
- **Tipo:** `EncryptedSharedPreferences` (con fallback a `SharedPreferences` normal)
- **Nombre:** `"inventario_session"`
- **Encriptación:** AES256_GCM

### **Claves de Almacenamiento**
| Clave | Tipo | Descripción |
|-------|------|-------------|
| `auth_token` | String | Token JWT de acceso |
| `refresh_token` | String | Token para renovar sesión |
| `token_expiry` | Long | Timestamp de expiración del token |
| `user_id` | String | ID del usuario actual |
| `user_name` | String | Nombre completo del usuario |
| `user_email` | String | Email del usuario |
| `user_role` | String | Rol: "OWNER", "ADMIN", "SELLER", "VIEWER" |
| `is_logged_in` | Boolean | Estado de sesión |
| `current_user` | String (JSON) | Objeto UserEntity serializado |
| `server_url` | String | URL del servidor API |

### **Métodos Principales**

#### Tokens
```kotlin
fun saveAuthToken(token: String, expiresIn: Long = 3600000)  // Guarda token con expiración
fun getAuthToken(): String?           // Obtiene token (null si expiró)
fun clearAuthToken()                  // Elimina token
fun saveRefreshToken(token: String)   // Guarda refresh token
fun getRefreshToken(): String?        // Obtiene refresh token
```

#### Usuario
```kotlin
fun saveCurrentUser(user: UserEntity)  // Guarda usuario y actualiza Flow
fun getCurrentUser(): UserEntity?      // Obtiene usuario desde prefs
fun getCurrentUserId(): String?        // Alias de getUserId()
fun getUserId(): String?
fun getUserName(): String?
fun getUserRole(): String?
```

#### Estado de Sesión
```kotlin
fun isLoggedIn(): Boolean   // Verifica si hay sesión válida (logged + token válido)
fun isOwner(): Boolean      // Verifica rol OWNER
fun isAdmin(): Boolean      // Verifica rol OWNER o ADMIN
```

#### Gestión de Sesión
```kotlin
fun createSession(token: String, refreshToken: String, user: UserEntity, expiresIn: Long)
fun clearSession()   // Limpia todos los datos
fun logout()         // Alias de clearSession()
```

#### Configuración
```kotlin
fun setServerUrl(url: String)
fun getServerUrl(): String   // Default: "https://inventariopy.ddns.net"
```

### **Flow Reactivo**
```kotlin
val currentUserFlow: Flow<UserEntity?>  // Emite cambios del usuario actual
```

### **Dependencias**
- `Gson` - Serialización de UserEntity
- `EncryptedSharedPreferences` - Almacenamiento seguro
- `MasterKey` - Clave de encriptación

### **Usado Por**
- `AuthRepository` - Login/logout
- `AuthInterceptor` - Obtener token para headers
- `LoginViewModel` - Verificar sesión existente
- `HomeViewModel` - Obtener nombre de usuario
- `ProductDetailViewModel` - Verificar permisos
- Todos los ViewModels que necesitan ID de usuario

---

## 🔗 AuthInterceptor.kt

### **Propósito**
Interceptor de OkHttp que agrega automáticamente el header `Authorization: Bearer {token}` a todas las peticiones HTTP, excepto login y registro.

### **Inyección de Dependencias**
```kotlin
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager
) : Interceptor
```

### **Lógica de Intercepción**
```
1. Recibe petición original
2. Si es ruta de auth (login/register) → NO agregar token
3. Si hay token disponible → Agregar headers:
   - Authorization: Bearer {token}
   - Content-Type: application/json
4. Continuar con la petición
```

### **Rutas Excluidas**
- `/auth/login`
- `/auth/register`

### **Configuración en AppModule**
```kotlin
@Provides
@Singleton
fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
    return OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        // ...
        .build()
}
```

---

## 💰 CurrencyUtils.kt

### **Propósito**
Utilidades para formateo de moneda paraguaya (Guaraníes - Gs.). **FUENTE ÚNICA** para todo formateo de moneda en la app.

### **Configuración Regional**
```kotlin
private val paraguayLocale = Locale("es", "PY")
private val decimalFormat = DecimalFormat("#,###")
```

### **Métodos de Formateo**

#### Formato Completo (con símbolo)
```kotlin
fun formatGs(amount: Long): String    // 150000 → "Gs. 150.000"
fun formatGs(amount: Double): String
fun formatGs(amount: Int): String

// Aliases (compatibilidad)
fun formatGuarani(amount: Long): String = formatGs(amount)
fun formatGuarani(amount: Double): String
fun formatGuarani(amount: Int): String
```

#### Formato para Input (sin símbolo)
```kotlin
fun formatForInput(amount: Long): String   // 150000 → "150.000"
fun formatForInput(amount: Int): String
```

#### Formato Corto (abreviado)
```kotlin
fun formatShort(amount: Long): String
// 1500000000 → "Gs. 1.5B"
// 1500000    → "Gs. 1.5M"
// 1500       → "Gs. 1.5K"
// 150        → "Gs. 150"
```

#### Solo Números
```kotlin
fun formatNumber(amount: Long): String  // 150000 → "150.000"
```

### **Métodos de Parsing**
```kotlin
fun parseGs(formatted: String): Long
// "Gs. 150.000" → 150000
// "150.000"     → 150000
// Maneja: espacios, puntos, comas, símbolo Gs.
```

### **Cálculos Financieros**
```kotlin
fun calculatePercentage(part: Long, total: Long): Float
// (50000, 100000) → 50.0f

fun formatPercentage(percentage: Float): String
// 50.0f → "50.0%"

fun calculateMargin(salePrice: Long, purchasePrice: Long): Float
// (150000, 100000) → 50.0f (50% de margen)

fun formatMargin(margin: Float): String
// 50.0f  → "+50.0%"
// -10.0f → "-10.0%"
```

### **Extensiones (en MissingTypes.kt)**
```kotlin
fun Long.formatGuarani(): String = CurrencyUtils.formatGuarani(this)
fun Int.formatGuarani(): String = CurrencyUtils.formatGuarani(this)
fun Double.formatGuarani(): String = CurrencyUtils.formatGuarani(this)
```

### **Usado Por**
- Todos los Adapters (ProductAdapter, CartAdapter, SaleAdapter, etc.)
- ProductDetailFragment - Mostrar precios
- CheckoutFragment - Totales de venta
- ReportsFragment - Estadísticas financieras
- Cualquier UI que muestre precios

---

## 📅 DateUtils.kt

### **Propósito**
Utilidades para formateo y manipulación de fechas. **FUENTE ÚNICA** para todo manejo de fechas en la app.

### **Formatos Disponibles**
| Formato | Patrón | Ejemplo |
|---------|--------|---------|
| Full | `dd/MM/yyyy HH:mm` | "08/01/2026 15:30" |
| Date Only | `dd/MM/yyyy` | "08/01/2026" |
| Time Only | `HH:mm` | "15:30" |
| Day Month | `dd MMM` | "08 ene" |

### **Métodos de Formateo**
```kotlin
fun formatFull(timestamp: Long): String       // Fecha y hora completa
fun formatDateOnly(timestamp: Long): String   // Solo fecha
fun formatTimeOnly(timestamp: Long): String   // Solo hora
fun formatDayMonth(timestamp: Long): String   // Día y mes abreviado

fun formatRelative(timestamp: Long): String
// < 1 min  → "Ahora"
// < 1 hora → "Hace X min"
// < 1 día  → "Hace X h"
// < 2 días → "Ayer"
// < 7 días → "Hace X días"
// >= 7 días → "dd/MM/yyyy"
```

### **Métodos de Verificación**
```kotlin
fun isToday(timestamp: Long): Boolean
fun isYesterday(timestamp: Long): Boolean
fun isThisWeek(timestamp: Long): Boolean
fun isThisMonth(timestamp: Long): Boolean
fun isThisYear(timestamp: Long): Boolean
```

### **Métodos de Rangos**
```kotlin
fun getStartOfDay(timestamp: Long = now): Long    // 00:00:00.000 del día
fun getEndOfDay(timestamp: Long = now): Long      // 23:59:59.999 del día
fun getStartOfWeek(): Long                        // Inicio de semana actual
fun getStartOfMonth(): Long                       // Día 1 del mes actual
fun getStartOfYear(): Long                        // 1 de enero del año actual
```

### **Usado Por**
- `SaleAdapter` - Mostrar fecha de ventas
- `ReportsViewModel` - Calcular rangos de reportes
- `SettingsFragment` - Mostrar última sincronización
- Cualquier UI que muestre fechas/horas

---

## 🛠️ Utils.kt

### **Propósito**
Archivo contenedor de múltiples utilidades organizadas en secciones: Constantes, Extensiones de View, Extensiones de Context, Extensiones de ImageView, FileUtils, ExcelUtils, Validators y Generators.

---

### **📌 Constants (Object)**

```kotlin
object Constants {
    // Moneda
    const val CURRENCY_SYMBOL = "Gs."
    
    // Formatos de fecha
    const val DATE_FORMAT_DISPLAY = "dd/MM/yyyy"
    const val DATE_FORMAT_TIME = "HH:mm"
    const val DATE_FORMAT_FULL = "dd/MM/yyyy HH:mm"
    const val DATE_FORMAT_API = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    const val DATE_FORMAT_FILE = "yyyyMMdd_HHmmss"
    
    // Request codes (legacy)
    const val REQUEST_CODE_CAMERA = 1001
    const val REQUEST_CODE_GALLERY = 1002
    const val REQUEST_CODE_BARCODE = 1003
    
    // Extras para Intent
    const val EXTRA_PRODUCT_ID = "product_id"
    const val EXTRA_SALE_ID = "sale_id"
    const val EXTRA_BARCODE = "barcode"
    const val EXTRA_SCAN_MODE = "scan_mode"
    
    // Modos de escaneo
    const val SCAN_MODE_ADD_PRODUCT = "add_product"
    const val SCAN_MODE_SEARCH = "search"
    const val SCAN_MODE_ADD_TO_CART = "add_to_cart"
}
```

---

### **👁️ Extensiones de View**

```kotlin
fun View.visible()                    // visibility = VISIBLE
fun View.invisible()                  // visibility = INVISIBLE
fun View.gone()                       // visibility = GONE
fun View.visibleIf(condition: Boolean) // VISIBLE si true, GONE si false

fun View.hideKeyboard()               // Oculta teclado
fun View.showSnackbar(message: String, duration: Int = SHORT)
fun View.showSnackbarWithAction(message: String, actionText: String, action: () -> Unit)
```

**Uso típico:**
```kotlin
binding.progressBar.visibleIf(isLoading)
binding.emptyState.gone()
binding.root.showSnackbar("Producto agregado")
```

---

### **📱 Extensiones de Context/Fragment**

```kotlin
fun Context.toast(message: String, duration: Int = Toast.LENGTH_SHORT)
fun Fragment.toast(message: String, duration: Int = Toast.LENGTH_SHORT)
```

---

### **🖼️ Extensiones de ImageView**

```kotlin
fun ImageView.loadImage(url: String?, placeholder: Int = R.drawable.ic_placeholder_product)
fun ImageView.loadImageFromFile(path: String?, placeholder: Int = R.drawable.ic_placeholder_product)
```

**Características:**
- Usa Glide internamente
- Crossfade automático
- Placeholder y error automáticos

---

### **📂 FileUtils (Object)**

```kotlin
object FileUtils {
    fun getImageFile(context: Context): File
    // Crea archivo temporal para imágenes en DIRECTORY_PICTURES
    // Nombre: IMG_{timestamp}.jpg
    
    fun getFileUri(context: Context, file: File): Uri
    // Obtiene URI usando FileProvider
    
    fun saveBitmapToFile(context: Context, bitmap: Bitmap): File
    // Guarda bitmap como JPEG (85% calidad)
    
    fun getExcelFile(context: Context, fileName: String): File
    // Crea archivo en DIRECTORY_DOCUMENTS
    // Nombre: {fileName}_{timestamp}.xlsx
}
```

---

### **📊 ExcelUtils (Object)**

```kotlin
object ExcelUtils {
    fun createExcelFile(
        context: Context,
        fileName: String,
        headers: List<String>,
        data: List<List<Any>>
    ): File
    // Crea archivo Excel usando Apache POI (XSSFWorkbook)
    // - Crea headers en fila 0
    // - Agrega datos en filas siguientes
    // - Auto-ajusta ancho de columnas
    
    fun shareFile(
        context: Context,
        file: File,
        mimeType: String = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    // Abre Intent.ACTION_SEND para compartir archivo
}
```

**Tipos de datos soportados:**
- `String` → Texto
- `Number` → Numérico (Double)
- `Boolean` → Booleano
- `Date` → Fecha
- Otros → `.toString()`

---

### **✅ Validators (Object)**

```kotlin
object Validators {
    fun isValidEmail(email: String): Boolean
    // Usa android.util.Patterns.EMAIL_ADDRESS
    
    fun isValidPhone(phone: String): Boolean
    // Formato paraguayo: 09XXXXXXXX o +595 9XXXXXXXX
    // Regex: ^(\+595|0)?9[0-9]{8}$
    
    fun isValidPassword(password: String): Boolean
    // Mínimo 6 caracteres
    
    fun isValidBarcode(barcode: String): Boolean
    // EAN-8, EAN-13, UPC-A: 8-13 dígitos numéricos
    // Regex: ^[0-9]{8,13}$
    
    fun isValidRuc(ruc: String): Boolean
    // RUC paraguayo: XXXXXX-X a XXXXXXXXX-X
    // Regex: ^[0-9]{6,9}-[0-9]$
}
```

---

### **🔑 Generators (Object)**

```kotlin
object Generators {
    fun generateId(): String
    // UUID aleatorio: "550e8400-e29b-41d4-a716-446655440000"
    
    fun generateSaleNumber(lastNumber: Int?): String
    // Número secuencial con padding: "00000001", "00000002"
    
    fun generateIdentifier(prefix: String = "PRD"): String
    // Identificador único: "PRD-123456-7890"
    // Formato: {prefix}-{últimos 6 dígitos timestamp}-{random 4 dígitos}
}
```

**Usado Por:**
- `AddProductViewModel` - Generar IDs de productos y variantes
- `SalesRepository` - Generar números de venta
- `ProductRepository` - Generar IDs de categorías/proveedores

---

### **📡 BootReceiver (BroadcastReceiver)**

```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Placeholder para programar sincronización con WorkManager
        }
    }
}
```

**Registro en AndroidManifest.xml:**
```xml
<receiver
    android:name=".utils.BootReceiver"
    android:enabled="true"
    android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

---

## 🔄 RELACIONES Y DEPENDENCIAS

```
┌─────────────────────────────────────────────────────────────┐
│                        AppModule                             │
│  (Provee SessionManager y AuthInterceptor como Singletons)  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────┐    ┌─────────────────┐
│ SessionManager  │◄───│ AuthInterceptor │
│   (Singleton)   │    │   (Singleton)   │
└────────┬────────┘    └────────┬────────┘
         │                      │
         │                      ▼
         │             ┌─────────────────┐
         │             │   OkHttpClient  │
         │             └─────────────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│            Repositories                  │
│  AuthRepository, ProductRepository,      │
│  SalesRepository, SyncRepository         │
└─────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│             ViewModels                   │
│  LoginViewModel, HomeViewModel,          │
│  ProductDetailViewModel, etc.            │
└─────────────────────────────────────────┘
```

---

## ⚠️ NOTAS IMPORTANTES

### Duplicaciones Conocidas (Pendientes de Limpiar)

1. **Formateo de moneda duplicado** en `Utils.kt`:
    - `Long.toGuaraniFormat()` - Duplica `CurrencyUtils.formatGs()`
    - `Long.toGuaraniInputFormat()` - Duplica `CurrencyUtils.formatForInput()`
    - `String.parseGuaraniToLong()` - Duplica `CurrencyUtils.parseGs()`

   **Recomendación:** Eliminar de Utils.kt, usar solo CurrencyUtils

2. **Formateo de fechas duplicado** en `Utils.kt`:
    - `Long.toDisplayDate()` - Duplica `DateUtils.formatDateOnly()`
    - `Long.toDisplayTime()` - Duplica `DateUtils.formatTimeOnly()`
    - `Long.toDisplayDateTime()` - Duplica `DateUtils.formatFull()`
    - `Long.toRelativeTime()` - Duplica `DateUtils.formatRelative()`

   **Recomendación:** Eliminar de Utils.kt, agregar extensiones bridge en DateUtils.kt

### Buenas Prácticas

1. **Siempre usar CurrencyUtils** para formatear precios
2. **Siempre usar DateUtils** para formatear fechas
3. **Siempre usar Generators.generateId()** para crear IDs únicos
4. **Siempre usar SessionManager** para acceder a datos de sesión
5. **Nunca acceder directamente a SharedPreferences** - usar SessionManager

---

## 📝 EJEMPLO DE USO TÍPICO

```kotlin
// En un Fragment
class ProductDetailFragment : Fragment() {
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Formatear precio
        binding.tvPrice.text = CurrencyUtils.formatGuarani(product.salePrice)
        
        // Formatear fecha
        binding.tvDate.text = DateUtils.formatRelative(product.createdAt)
        
        // Cargar imagen
        binding.ivProduct.loadImage(product.imageUrl)
        
        // Mostrar/ocultar vistas
        binding.progressBar.visibleIf(isLoading)
        binding.content.visible()
        
        // Mostrar mensaje
        binding.root.showSnackbar("Producto cargado")
    }
}

// En un ViewModel
class AddProductViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {
    
    fun createProduct(name: String) {
        val product = ProductEntity(
            id = Generators.generateId(),
            identifier = Generators.generateIdentifier("PRD"),
            name = name,
            // ...
        )
        
        val userId = sessionManager.getUserId()
        val isOwner = sessionManager.isOwner()
    }
}
```

---

## 🧪 TESTING

Para testing, se puede mockear `SessionManager`:

```kotlin
@Test
fun `test with mocked session`() {
    val mockSession = mockk<SessionManager>()
    every { mockSession.getUserId() } returns "test-user-id"
    every { mockSession.isOwner() } returns true
    
    // Usar en ViewModel
}
```

---

> **Generado para:** InventarioPy Android App  
> **Versión del documento:** 1.0  
> **Autor:** Documentación automática