package cl.cmitjans.rastreadores;

/**
 * Lee consultas DNS que llegan por la VPN local (paquetes IPv4 + UDP) y arma los paquetes de respuesta.
 * No usa nada de Android, así que se puede probar en un computador.
 */
final class DnsPacket {
    private DnsPacket() {}

    /** Una consulta DNS: quién preguntó (IP y puerto) y qué nombre preguntó. */
    static final class Query {
        byte[] srcIp = new byte[4];
        byte[] dstIp = new byte[4];
        int srcPort;
        int dstPort;
        byte[] dns;       // la consulta DNS tal cual, para reenviarla
        String name;      // por ejemplo "graph.facebook.com"
        int type;         // 1 = A (IPv4), 28 = AAAA (IPv6), 65 = HTTPS...
    }

    /** Devuelve la consulta, o null si el paquete no es una consulta DNS por UDP/IPv4. */
    static Query parse(byte[] p, int len) {
        if (len < 28) return null;
        int version = (p[0] >> 4) & 0xF;
        if (version != 4) return null;
        int ihl = (p[0] & 0xF) * 4;
        if (ihl < 20 || len < ihl + 8) return null;
        int protocol = p[9] & 0xFF;
        if (protocol != 17) return null; // solo UDP
        int totalLen = u16(p, 2);
        if (totalLen > len || totalLen < ihl + 8) return null;

        Query q = new Query();
        System.arraycopy(p, 12, q.srcIp, 0, 4);
        System.arraycopy(p, 16, q.dstIp, 0, 4);
        q.srcPort = u16(p, ihl);
        q.dstPort = u16(p, ihl + 2);
        if (q.dstPort != 53) return null;
        int udpLen = u16(p, ihl + 4);
        int dnsLen = Math.min(udpLen - 8, totalLen - ihl - 8);
        if (dnsLen < 12) return null;
        q.dns = new byte[dnsLen];
        System.arraycopy(p, ihl + 8, q.dns, 0, dnsLen);

        // Pregunta: nombre en etiquetas ("3www6google3com0") seguido del tipo.
        int qdcount = u16(q.dns, 4);
        if (qdcount < 1) return null;
        StringBuilder sb = new StringBuilder();
        int i = 12;
        while (i < dnsLen) {
            int l = q.dns[i] & 0xFF;
            if (l == 0) { i++; break; }
            if ((l & 0xC0) != 0 || i + 1 + l > dnsLen) return null; // las consultas no usan compresión
            if (sb.length() > 0) sb.append('.');
            for (int k = 0; k < l; k++) sb.append((char) (q.dns[i + 1 + k] & 0xFF));
            i += 1 + l;
        }
        if (i + 2 > dnsLen) return null;
        q.name = sb.toString().toLowerCase();
        q.type = u16(q.dns, i);
        return q;
    }

    /** Arma el paquete IPv4 + UDP que devuelve la respuesta DNS a quien preguntó. */
    static byte[] response(Query q, byte[] dns, int dnsLen) {
        int total = 20 + 8 + dnsLen;
        byte[] p = new byte[total];
        p[0] = 0x45;                 // IPv4, cabecera de 20 bytes
        put16(p, 2, total);
        p[6] = 0x40;                 // no fragmentar
        p[8] = 64;                   // TTL
        p[9] = 17;                   // UDP
        System.arraycopy(q.dstIp, 0, p, 12, 4); // responde "desde" el servidor DNS al que preguntó
        System.arraycopy(q.srcIp, 0, p, 16, 4);
        put16(p, 10, checksum(p, 0, 20));
        put16(p, 20, q.dstPort);
        put16(p, 22, q.srcPort);
        put16(p, 24, 8 + dnsLen);
        // suma de verificación UDP en 0: permitido en IPv4
        System.arraycopy(dns, 0, p, 28, dnsLen);
        return p;
    }

    /** Respuesta "ese nombre no existe" (para bloquear en una versión futura). */
    static byte[] nxdomain(Query q) {
        byte[] d = q.dns.clone();
        d[2] = (byte) (0x80 | (d[2] & 0x79)); // es respuesta, mismo código de operación, recursión deseada
        d[3] = (byte) 0x83;                    // recursión disponible, código 3 = el nombre no existe
        d[6] = d[7] = d[8] = d[9] = d[10] = d[11] = 0;
        return response(q, d, d.length);
    }

    static int u16(byte[] b, int o) {
        return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF);
    }

    static void put16(byte[] b, int o, int v) {
        b[o] = (byte) (v >> 8);
        b[o + 1] = (byte) v;
    }

    static int checksum(byte[] b, int off, int len) {
        long sum = 0;
        for (int i = 0; i < len; i += 2) {
            int hi = b[off + i] & 0xFF;
            int lo = i + 1 < len ? b[off + i + 1] & 0xFF : 0;
            sum += (hi << 8) | lo;
        }
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        return (int) (~sum & 0xFFFF);
    }
}
