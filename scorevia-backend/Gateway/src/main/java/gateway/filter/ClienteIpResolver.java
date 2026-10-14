package gateway.filter;

import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

/**
 * Resuelve la IP del cliente real cuando el Gateway está detrás de un único proxy de
 * confianza (p. ej. un AWS ALB): {@code getRemoteAddress()} devolvería la IP del propio
 * proxy, no la del cliente, así que sin esto cualquier rate-limit por IP se convertiría en
 * un cupo compartido entre TODOS los usuarios en vez de uno por IP.
 * <p>
 * Se toma el ÚLTIMO valor de {@code X-Forwarded-For}, no el primero: un proxy como ALB
 * añade la IP del cliente al FINAL de la cabecera en vez de sobrescribirla, así que
 * cualquier valor anterior en la lista puede haberlo puesto el propio cliente para intentar
 * suplantar su IP. Solo el último salto (el que añadió el proxy de confianza) es fiable.
 * </p>
 * <p>
 * <b>Requisito de despliegue:</b> esto solo es seguro si el Gateway NO es alcanzable
 * directamente desde fuera de la red del proxy (en AWS: el security group del
 * contenedor/instancia debe permitir tráfico entrante únicamente desde el ALB). Si el
 * Gateway quedara expuesto también de forma directa, cualquiera podría saltarse el ALB y
 * falsificar esta cabecera libremente.
 * </p>
 */
final class ClienteIpResolver {

    private ClienteIpResolver() {
    }

    static String resolver(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // split(",", -1) conserva los segmentos vacíos finales; recorremos desde el final
            // saltando huecos en vez de asumir que el último segmento es no-vacío, ya que una
            // cabecera malformada (p. ej. "X-Forwarded-For: ,") no debe tumbar la petición.
            String[] partes = forwardedFor.split(",", -1);
            for (int i = partes.length - 1; i >= 0; i--) {
                String candidato = partes[i].trim();
                if (!candidato.isEmpty()) {
                    return candidato;
                }
            }
        }

        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null && remoteAddress.getAddress() != null
                ? remoteAddress.getAddress().getHostAddress()
                : "desconocida";
    }
}
