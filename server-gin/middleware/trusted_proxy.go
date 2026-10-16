package middleware

import (
	"fmt"
	"net"
	"os"
	"strings"

	"github.com/gin-gonic/gin"
)

const trustedProxyEnv = "CATLINK_TRUSTED_PROXY_CIDRS"

func ConfigureTrustedProxies(engine *gin.Engine) error {
	entries, _, err := trustedProxyEntries()
	if err != nil {
		return err
	}
	return engine.SetTrustedProxies(entries)
}

func IsTrustedProxyAddress(address string) bool {
	ip := parseProxyIP(address)
	if ip == nil {
		return false
	}
	_, networks, err := trustedProxyEntries()
	if err != nil {
		return false
	}
	for _, network := range networks {
		if network.Contains(ip) {
			return true
		}
	}
	return false
}

func trustedProxyEntries() ([]string, []*net.IPNet, error) {
	raw := strings.TrimSpace(os.Getenv(trustedProxyEnv))
	if raw == "" {
		return nil, nil, nil
	}
	var entries []string
	var networks []*net.IPNet
	for _, value := range strings.Split(raw, ",") {
		value = strings.TrimSpace(value)
		if value == "" {
			continue
		}
		if ip := net.ParseIP(value); ip != nil {
			bits := 128
			if ipv4 := ip.To4(); ipv4 != nil {
				ip = ipv4
				bits = 32
			}
			entries = append(entries, value)
			networks = append(networks, &net.IPNet{IP: ip, Mask: net.CIDRMask(bits, bits)})
			continue
		}
		_, network, err := net.ParseCIDR(value)
		if err != nil {
			return nil, nil, fmt.Errorf("invalid %s entry %q", trustedProxyEnv, value)
		}
		entries = append(entries, value)
		networks = append(networks, network)
	}
	return entries, networks, nil
}

func parseProxyIP(address string) net.IP {
	address = strings.TrimSpace(address)
	if host, _, err := net.SplitHostPort(address); err == nil {
		address = host
	}
	return net.ParseIP(strings.Trim(address, "[]"))
}
