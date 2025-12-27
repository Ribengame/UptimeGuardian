package com.uptimeguardian.core.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Monitor(
    val id: String,
    val name: String,
    val url: String,
    val type: MonitorType,
    val interval: Int, // in seconds
    val timeout: Int, // in seconds
    val method: HttpMethod = HttpMethod.GET,
    val expectedStatusCode: Int = 200,
    val expectedString: String? = null,
    val sslCheck: Boolean = true,
    val sslExpiryWarningDays: Int = 30,
    val retries: Int = 0,
    val retryInterval: Int = 30,
    val tags: List<String> = emptyList(),
    val notifications: List<NotificationConfig> = emptyList(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val isActive: Boolean = true,
    val advancedSettings: AdvancedSettings = AdvancedSettings()
) {
    val uptime: Double = 0.0 // Will be calculated from heartbeat data
    val avgResponseTime: Double = 0.0
}

@Serializable
enum class MonitorType {
    HTTP, PING, TCP, UDP, DNS, HEARTBEAT, KEYWORD
}

@Serializable
enum class HttpMethod {
    GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH
}

@Serializable
data class NotificationConfig(
    val id: String,
    val type: NotificationType,
    val name: String,
    val config: Map<String, String> = emptyMap(),
    val enabled: Boolean = true
)

@Serializable
enum class NotificationType {
    PUSH, EMAIL, WEBHOOK, TELEGRAM, DISCORD, SLACK, SMS, CALL
}

@Serializable
data class AdvancedSettings(
    val followRedirects: Boolean = true,
    val acceptInvalidCerts: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val auth: AuthConfig? = null,
    val proxy: ProxyConfig? = null
)

@Serializable
data class AuthConfig(
    val type: AuthType,
    val username: String? = null,
    val password: String? = null,
    val token: String? = null
)

@Serializable
enum class AuthType {
    BASIC, BEARER, NTLM
}

@Serializable
data class ProxyConfig(
    val host: String,
    val port: Int,
    val auth: AuthConfig? = null
)

@Serializable
data class Heartbeat(
    val id: String,
    val monitorId: String,
    val status: HeartbeatStatus,
    val ping: Int? = null,
    val statusCode: Int? = null,
    val message: String? = null,
    val createdAt: Instant = Instant.now(),
    val sslInfo: SslInfo? = null,
    val responseTime: Int? = null
)

@Serializable
enum class HeartbeatStatus {
    UP, DOWN, PENDING, MAINTENANCE
}

@Serializable
data class SslInfo(
    val validFrom: Instant,
    val validTo: Instant,
    val issuer: String,
    val daysRemaining: Int
)

@Serializable
data class Incident(
    val id: String,
    val monitorId: String,
    val startTime: Instant,
    val endTime: Instant? = null,
    val resolved: Boolean = false,
    val acknowledged: Boolean = false,
    val acknowledgedAt: Instant? = null,
    val acknowledgedBy: String? = null,
    val notificationsSent: List<Instant> = emptyList()
)

@Serializable
data class UptimeStats(
    val monitorId: String,
    val period: StatsPeriod,
    val uptimePercentage: Double,
    val totalChecks: Int,
    val successfulChecks: Int,
    val failedChecks: Int,
    val avgResponseTime: Double,
    val incidents: List<Incident>,
    val createdAt: Instant = Instant.now()
)

@Serializable
enum class StatsPeriod {
    DAY, WEEK, MONTH, QUARTER, YEAR, ALL
}