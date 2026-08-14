# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

LeonTools is a collection of industrial robot programming and control tools for **ELITE Robots** (EC/CS series collaborative robots) and **AUBO Robots** (i5 series). The primary domain is robotic welding automation — weld seam tracking, touch sensing, arc tracking, and trajectory visualization.

## Repository Structure

```
LeonTools/
├── Web/                          # Web-based tools
│   ├── createTrace.html          # 3D trajectory visualizer (Plotly.js)
│   └── nginxSetting              # Nginx config for serving the tool
├── Python/
│   ├── ELITE/                    # ELITE robot tools
│   │   ├── robot_start.py        # Robot power-on + brake release via Dashboard (port 29999)
│   │   ├── temp代码脚本查看.py    # Large reference script (generated EliScript)
│   │   ├── daemon/
│   │   │   └── daemon.py         # XML-RPC server for remote robot control + HTTP status page
│   │   ├── demo/                 # Generated EliScript programs (welding scenarios)
│   │   │   ├── 1.无摆动.py       # Basic weld tracking
│   │   │   ├── 2.增加摆动.py     # Weave welding
│   │   │   ├── 3.增加电弧跟踪.py # Arc tracking
│   │   │   └── 4.增加多层.py     # Multi-layer welding
│   │   ├── Tools/
│   │   │   └── drawCSV.py        # Offline trajectory plotter (Plotly, parses movep data)
│   │   ├── math/
│   │   │   └── pointPlace2Y.py   # Point-in-half-space check under user coordinate system
│   │   └── robotAct/
│   │       └── robotAct.py       # SDK import stub (elite_cs_sdk)
│   └── aubo/pe/示例/
│       └── user2base_Cir.py      # AUBO i5 robot SDK wrapper (libpyauboi5)
└── Elite-pluginTool/             # ELITE teach-pendant plugin packages
    ├── 1.测试学习/                # Test/learning plugins (.zip)
    └── 2.上下电/                  # Power on/off plugin (.elico, .zip)
```

## Key Technical Details

### ELITE Robot Communication

- **Dashboard protocol**: Raw TCP socket to port **29999** — send text commands like `robotControl -on`, `brakeRelease`, `play`, `task -p <path>`
- **XML-RPC daemon** (`daemon.py`): Runs on `0.0.0.0:3333` (RPC) + `0.0.0.0:5555` (HTTP). Exposes: `robot_start`, `robot_stop`, `load_task`, `play_task`, `pause_task`, `stop_task`, `robot_moveJ`, `set_message`
- The daemon spawns two threads — HTTP server + XML-RPC server — and uses a global `message` variable for inter-thread communication
- Default robot IP throughout the code: `192.168.249.128`

### EliScript Language (ELITE's DSL)

The `.py` files in `demo/` are **EliScript** programs (not standard Python). Key constructs:
- Motion: `movej()`, `movel()`, `movep()`, `movec()` — joint, linear, process, circular moves
- IK: `get_inverse_kin(pose, qnear=...)` — inverse kinematics
- Welding: `full_apply_touch_offset(pose)` — applies touch-sensed offset to a target pose
- Frames: `full_tracking_frame` — coordinate reference marker, `pose_trans()`, `pose_trans_local()`, `pose_trans_world()`
- Globals: Heavily uses `global` for shared state (offset values, reference points, process flags)
- Threading: `start_thread()`, `stop_thread()`, `sync()`
- Welding-specific: `full_arc_on(process)`, `full_arc_off()`, `full_searching_touch_sense()`
- Poses are `[x, y, z, rx, ry, rz]` arrays (position in meters, rotation in radians)

### AUBO Robot SDK (`user2base_Cir.py`)

A comprehensive Python wrapper around `libpyauboi5` C library. The `Auboi5Robot` class provides:
- Connection management (login/logout on port 8899)
- All motion types: `move_joint`, `move_line`, `move_rotate`, `move_track`
- Offline trajectory: `append_offline_track_waypoint`, `startup_offline_track`
- Coordinate transforms: `base_to_user`, `user_to_base`, `base_to_base_additional_tool`
- Kinematics: `forward_kin`, `inverse_kin`, `rpy_to_quaternion`
- IO, collision detection, tool dynamics, and event handling
- Error/event type enums (`RobotEventType`, `RobotErrorType`, `RobotStatus`, etc.)

### Trajectory Visualization (`createTrace.html`)

Browser-based tool using Plotly.js + PapaParse:
- Parses EliScript `movep()` calls to extract `[x, y, z]` coordinates
- Parses `full_tracking_frame = [...]` assignments as reference markers
- Auto-detects Z-axis breaks (>5mm) to show lift-off gaps
- Renders both 3D scatter and 2D XY projection
- Click on any point to inspect/copy coordinates
- Falls back to CSV parsing if no EliScript patterns match

## Git Workflow

- **Main branch**: `master`
- **Development branch**: `dev`
- Commit messages in Chinese, format: `feat: <description>`
- Remote: Gitee (not GitHub)

## Development Notes

- No build system, package manager, or test framework exists — all scripts are standalone
- Chinese is the primary language for comments, variable names, and documentation
- Python scripts require `numpy` and `plotly` for visualization tools
- The AUBO SDK requires the proprietary `libpyauboi5` shared library (not in this repo)
- The ELITE SDK requires `elite_cs_sdk` Python package (not in this repo)
- The `temp代码脚本查看.py` file (48KB) is a reference dump of a generated EliScript program — treat as read-only reference data
