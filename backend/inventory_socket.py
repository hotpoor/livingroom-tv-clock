"""Authenticated RFC6455 notifications; inventory always travels through HTTP CAS."""
import base64
import hashlib
import json
import struct
import threading


class InventorySocket:
    def __init__(self):
        self.clients = set()
        self.lock = threading.Lock()

    def notify(self):
        with self.lock:
            clients = list(self.clients)
        for client in clients:
            client.set()

    def serve(self, h):
        key = h.headers.get('Sec-WebSocket-Key', '')
        try:
            valid = len(base64.b64decode(key, validate=True)) == 16
        except ValueError:
            valid = False
        if not valid or h.headers.get('Upgrade', '').lower() != 'websocket' or h.headers.get('Sec-WebSocket-Version') != '13':
            h.send_error(400)
            return
        accept = base64.b64encode(hashlib.sha1((key+'258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode()).digest()).decode()
        h.send_response(101)
        h.send_header('Upgrade', 'websocket')
        h.send_header('Connection', 'Upgrade')
        h.send_header('Sec-WebSocket-Accept', accept)
        h.end_headers()
        h.wfile.flush()
        event = threading.Event()
        with self.lock:
            self.clients.add(event)
        h.connection.settimeout(25)
        alive = threading.Event()
        alive.set()
        write_lock = threading.Lock()

        def send(op, data):
            with write_lock:
                h.wfile.write(bytes([0x80 | op, len(data)]) + data)
                h.wfile.flush()

        def read_exact(n):
            result = h.rfile.read(n)
            if len(result) != n:
                raise EOFError()
            return result

        def reader():
            try:
                while alive.is_set():
                    first, second = read_exact(2)
                    length = second & 127
                    if length == 126:
                        length = struct.unpack('!H', read_exact(2))[0]
                    if length > 125 or not second & 128 or not first & 128:
                        break
                    mask = read_exact(4)
                    data = bytes(b ^ mask[i % 4] for i, b in enumerate(read_exact(length)))
                    op = first & 15
                    if op == 8:
                        send(8, data)
                        break
                    if op == 9:
                        send(10, data)
            except (OSError, EOFError):
                pass
            finally:
                alive.clear()
                event.set()

        threading.Thread(target=reader, daemon=True).start()
        try:
            send(1, b'{"type":"connected"}')
            while alive.is_set():
                changed = event.wait(10)
                event.clear()
                if not alive.is_set():
                    break
                send(1, b'{"type":"inventory_changed"}') if changed else send(9, b'alive')
        except OSError:
            pass
        finally:
            alive.clear()
            with self.lock:
                self.clients.discard(event)
            h.close_connection = True
