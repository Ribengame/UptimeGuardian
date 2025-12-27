package com.uptimeguardian.core.data.repository

import com.uptimeguardian.core.data.datasource.local.MonitorDao
import com.uptimeguardian.core.data.datasource.local.HeartbeatDao
import com.uptimeguardian.core.data.datasource.local.IncidentDao
import com.uptimeguardian.core.data.datasource.local.StatsDao
import com.uptimeguardian.core.data.datasource.remote.UptimeApi
import com.uptimeguardian.core.domain.model.*
import com.uptimeguardian.core.domain.repository.MonitorRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import javax.inject.Inject

class MonitorRepositoryImpl @Inject constructor(
    private val monitorDao: MonitorDao,
    private val heartbeatDao: HeartbeatDao,
    private val incidentDao: IncidentDao,
    private val statsDao: StatsDao,
    private val api: UptimeApi
) : MonitorRepository {
    
    override fun getAllMonitors(): Flow<List<Monitor>> {
        return monitorDao.getAll().map { monitors ->
            monitors.map { it.toDomain() }
        }
    }
    
    override fun getActiveMonitors(): Flow<List<Monitor>> {
        return monitorDao.getActive().map { monitors ->
            monitors.map { it.toDomain() }
        }
    }
    
    override suspend fun getMonitor(id: String): Monitor? {
        return monitorDao.getById(id)?.toDomain()
    }
    
    override suspend fun addMonitor(monitor: Monitor) {
        monitorDao.insert(monitor.toEntity())
    }
    
    override suspend fun updateMonitor(monitor: Monitor) {
        monitorDao.update(monitor.toEntity())
    }
    
    override suspend fun deleteMonitor(id: String) {
        monitorDao.delete(id)
    }
    
    override suspend fun toggleMonitor(id: String, active: Boolean) {
        monitorDao.updateActive(id, active, Clock.System.now())
    }
    
    override fun getHeartbeats(monitorId: String, limit: Int): Flow<List<Heartbeat>> {
        return heartbeatDao.getByMonitorId(monitorId, limit).map { heartbeats ->
            heartbeats.map { it.toDomain() }
        }
    }
    
    override suspend fun addHeartbeat(heartbeat: Heartbeat) {
        heartbeatDao.insert(heartbeat.toEntity())
    }
    
    override fun getIncidents(monitorId: String): Flow<List<Incident>> {
        return incidentDao.getByMonitorId(monitorId).map { incidents ->
            incidents.map { it.toDomain() }
        }
    }
    
    override suspend fun addIncident(incident: Incident) {
        incidentDao.insert(incident.toEntity())
    }
    
    override suspend fun updateIncident(incident: Incident) {
        incidentDao.update(incident.toEntity())
    }
    
    override fun getStats(monitorId: String, period: StatsPeriod): Flow<UptimeStats?> {
        return statsDao.getStats(monitorId, period).map { it?.toDomain() }
    }
    
    override suspend fun calculateStats(monitorId: String) {
        // Calculate uptime stats from heartbeats
        val heartbeats = heartbeatDao.getHeartbeatsForStats(monitorId)
        val incidents = incidentDao.getByMonitorIdSync(monitorId)
        
        // Calculate for different periods
        StatsPeriod.values().forEach { period ->
            val periodHeartbeats = heartbeats.filter { isInPeriod(it.createdAt, period) }
            val stats = calculateStatsFromHeartbeats(periodHeartbeats, incidents, period)
            statsDao.insert(stats.toEntity())
        }
    }
    
    private fun isInPeriod(instant: Instant, period: StatsPeriod): Boolean {
        val now = Clock.System.now()
        return when (period) {
            StatsPeriod.DAY -> instant.epochSeconds > now.minus(24 * 60 * 60, TimeUnit.SECONDS)
            StatsPeriod.WEEK -> instant.epochSeconds > now.minus(7 * 24 * 60 * 60, TimeUnit.SECONDS)
            StatsPeriod.MONTH -> instant.epochSeconds > now.minus(30 * 24 * 60 * 60, TimeUnit.SECONDS)
            StatsPeriod.QUARTER -> instant.epochSeconds > now.minus(90 * 24 * 60 * 60, TimeUnit.SECONDS)
            StatsPeriod.YEAR -> instant.epochSeconds > now.minus(365 * 24 * 60 * 60, TimeUnit.SECONDS)
            StatsPeriod.ALL -> true
        }
    }
    
    private fun calculateStatsFromHeartbeats(
        heartbeats: List<HeartbeatEntity>,
        incidents: List<IncidentEntity>,
        period: StatsPeriod
    ): UptimeStats {
        val total = heartbeats.size
        val successful = heartbeats.count { it.status == HeartbeatStatus.UP }
        val failed = heartbeats.count { it.status == HeartbeatStatus.DOWN }
        val uptime = if (total > 0) successful.toDouble() / total * 100 else 100.0
        val avgResponseTime = heartbeats.mapNotNull { it.responseTime }.average()
        
        return UptimeStats(
            monitorId = heartbeats.firstOrNull()?.monitorId ?: "",
            period = period,
            uptimePercentage = uptime,
            totalChecks = total,
            successfulChecks = successful,
            failedChecks = failed,
            avgResponseTime = avgResponseTime,
            incidents = incidents.map { it.toDomain() }
        )
    }
}