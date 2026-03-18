import psutil
from typing import Dict, Any


def collect_os_metrics() -> Dict[str, Any]:
    """Return a snapshot of OS-level resource metrics."""
    cpu = psutil.cpu_percent(interval=1)
    ram = psutil.virtual_memory()
    disk = psutil.disk_usage("/")
    net = psutil.net_io_counters()
    load = psutil.getloadavg()

    return {
        "cpu_percent": round(cpu, 1),
        "cpu_core_count": psutil.cpu_count(logical=False) or 1,
        "cpu_logical_count": psutil.cpu_count(logical=True) or 1,
        "ram_percent": round(ram.percent, 1),
        "ram_used_gb": round(ram.used / 1024 ** 3, 2),
        "ram_total_gb": round(ram.total / 1024 ** 3, 2),
        "disk_percent": round(disk.percent, 1),
        "disk_used_gb": round(disk.used / 1024 ** 3, 2),
        "disk_total_gb": round(disk.total / 1024 ** 3, 2),
        "net_bytes_sent_mb": round(net.bytes_sent / 1024 ** 2, 2),
        "net_bytes_recv_mb": round(net.bytes_recv / 1024 ** 2, 2),
        "load_avg_1m": round(load[0], 2),
        "load_avg_5m": round(load[1], 2),
        "load_avg_15m": round(load[2], 2),
    }
