"""Elite 30001 primary interface: script upload and state stream."""
from __future__ import annotations
import socket, struct, threading, time

class Elite30001:
    def __init__(self, log=None):
        self.log = log or (lambda *_: None); self.sock=None; self.running=False
        self.speed = None; self.pose = None; self.updated_at = None; self._last_pose = None; self._last_time = None; self._thread=None
    @property
    def connected(self): return self.sock is not None and self.running
    def connect(self, host, port=30001, timeout=5):
        self.close(); self.sock=socket.create_connection((host, port), timeout)
        self.sock.settimeout(1); self.running=True
        self._thread=threading.Thread(target=self._reader, daemon=True); self._thread.start()
        self.log('sys', f'已连接 {host}:{port} (30001)')
    def close(self):
        self.running=False
        if self.sock:
            try: self.sock.shutdown(socket.SHUT_RDWR)
            except OSError: pass
            try: self.sock.close()
            except OSError: pass
        self.sock=None
    def send_script(self, script):
        if not self.sock: raise RuntimeError('未连接控制器')
        data=script.rstrip()+'\n'
        self.sock.sendall(data.encode('utf-8')); self.log('tx', data.rstrip())
    def _reader(self):
        buf=b''
        while self.running and self.sock:
            try: chunk=self.sock.recv(4096)
            except socket.timeout: continue
            except OSError: break
            if not chunk: break
            buf += chunk
            while len(buf)>=5:
                n, typ=struct.unpack('>IB', buf[:5])
                if n<5 or n>1_000_000: buf=buf[1:]; continue
                if len(buf)<n: break
                packet=buf[:n]; buf=buf[n:]
                self._parse(packet, typ)
        self.running=False
    def _parse(self, packet, typ):
        # Elite/UR-compatible CartesianInfo subpackage. Keep offsets configurable
        # because controller firmware revisions may change the state layout.
        i=5
        while i+5<=len(packet):
            n, sub=struct.unpack('>IB', packet[i:i+5])
            if n<5 or i+n>len(packet):
                # Some Elite firmware appends non-standard bytes after the
                # normal subpackages. Do not discard an otherwise valid packet.
                i += 1
                continue
            body=packet[i+5:i+n]
            if sub==4 and len(body)>=48:  # CartesianInfo: pose then TCP speed
                vals=struct.unpack('>6d6d', body[:96]) if len(body)>=96 else None
                if vals:
                    now=time.time(); pose=list(vals[:6])
                    if self._last_pose is not None and self._last_time is not None:
                        dt=now-self._last_time
                        if dt>0:
                            # Position is m. The first three values are TCP
                            # linear velocity in the base frame, estimated
                            # from consecutive real TCP poses.
                            self.speed=[(pose[j]-self._last_pose[j])/dt for j in range(3)] + [0.0,0.0,0.0]
                            if sum(abs(x) for x in self.speed[:3]) < 1e-4: self.speed[:3]=[0.0,0.0,0.0]
                    self.pose=pose; self._last_pose=pose; self._last_time=now; self.updated_at=now
            i += n
    def snapshot(self): return {'connected':self.connected,'tcp_speed':self.speed,'tcp_pose':self.pose,'updated_at':self.updated_at}
