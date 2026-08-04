package happicore

import (
	"context"
	"encoding/json"
	"fmt"
	"net/url"
	"strings"
	"sync"
	"time"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/adapter/endpoint"
	"github.com/sagernet/sing-box/adapter/inbound"
	"github.com/sagernet/sing-box/adapter/outbound"
	boxService "github.com/sagernet/sing-box/adapter/service"
	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing-box/protocol/direct"
	"github.com/sagernet/sing-box/protocol/socks"
	"github.com/sagernet/sing-box/protocol/tun"
	"github.com/sagernet/sing-box/protocol/vless"
	C "github.com/sagernet/sing/common/json"

	_ "golang.org/x/mobile/bind"
)

var (
	instance *box.Box
	instCtx  context.Context
	instStop context.CancelFunc
	mu       sync.Mutex

	tunFd    int64
	hasTunFd bool

	rxTotal int64
	txTotal int64
	statsWg sync.WaitGroup
)

type VLESSConfig struct {
	UUID          string `json:"uuid"`
	Address       string `json:"address"`
	Port          string `json:"port"`
	Security      string `json:"security"`
	Encryption    string `json:"encryption"`
	Type          string `json:"type"`
	HeaderType    string `json:"headerType"`
	Flow          string `json:"flow"`
	FP            string `json:"fp"`
	AllowInsecure string `json:"allowInsecure"`
	Name          string `json:"name"`
}

func ParseVLESS(link string) (string, error) {
	if !strings.HasPrefix(link, "vless://") {
		return "", fmt.Errorf("not a vless link")
	}
	link = link[8:]

	hashIdx := strings.Index(link, "#")
	var fragment string
	if hashIdx >= 0 {
		fragment = link[hashIdx+1:]
		link = link[:hashIdx]
	}

	atIdx := strings.Index(link, "@")
	if atIdx < 0 {
		return "", fmt.Errorf("missing @ in vless link")
	}
	uuid := link[:atIdx]
	afterAt := link[atIdx+1:]

	qIdx := strings.Index(afterAt, "?")
	var hostPort, queryStr string
	if qIdx >= 0 {
		hostPort = afterAt[:qIdx]
		queryStr = afterAt[qIdx+1:]
	} else {
		hostPort = afterAt
	}

	colonIdx := strings.LastIndex(hostPort, ":")
	var address, port string
	if colonIdx >= 0 {
		address = hostPort[:colonIdx]
		port = hostPort[colonIdx+1:]
	} else {
		address = hostPort
		port = "443"
	}

	cfg := VLESSConfig{UUID: uuid, Address: address, Port: port}
	if fragment != "" {
		if decoded, err := url.QueryUnescape(fragment); err == nil {
			cfg.Name = decoded
		} else {
			cfg.Name = fragment
		}
	}
	if queryStr != "" {
		params, _ := url.ParseQuery(queryStr)
		cfg.Security = params.Get("security")
		cfg.Encryption = params.Get("encryption")
		cfg.Type = params.Get("type")
		cfg.HeaderType = params.Get("headerType")
		cfg.Flow = params.Get("flow")
		cfg.FP = params.Get("fp")
		cfg.AllowInsecure = params.Get("allowInsecure")
	}

	data, _ := json.MarshalIndent(cfg, "", "  ")
	return string(data), nil
}

func GenerateSocksConfig(vlessLink string, socksPort int) (string, error) {
	jsonStr, err := ParseVLESS(vlessLink)
	if err != nil {
		return "", err
	}
	var cfg VLESSConfig
	if err := json.Unmarshal([]byte(jsonStr), &cfg); err != nil {
		return "", err
	}

	serverPort := 443
	if cfg.Port != "" {
		fmt.Sscanf(cfg.Port, "%d", &serverPort)
	}

	var tlsObj map[string]any
	if cfg.Security == "tls" || cfg.Security == "reality" {
		tlsObj = map[string]any{
			"enabled":     true,
			"server_name": cfg.Address,
			"insecure":    cfg.AllowInsecure == "1",
		}
		if cfg.FP != "" {
			tlsObj["utls"] = map[string]any{
				"enabled":     true,
				"fingerprint": cfg.FP,
			}
		}
		if cfg.Security == "reality" {
			tlsObj["reality"] = map[string]any{
				"enabled": true,
			}
		}
	}

	var transportObj map[string]any
	switch cfg.Type {
	case "ws", "websocket":
		transportObj = map[string]any{
			"type": "ws",
			"path": "/",
		}
	case "grpc":
		transportObj = map[string]any{
			"type":         "grpc",
			"service_name": "",
		}
	case "quic":
		transportObj = map[string]any{
			"type": "quic",
		}
	case "httpupgrade":
		transportObj = map[string]any{
			"type": "httpupgrade",
		}
	}

	outbound := map[string]any{
		"type":        "vless",
		"tag":         "proxy",
		"server":      cfg.Address,
		"server_port": serverPort,
		"uuid":        cfg.UUID,
	}
	if cfg.Flow != "" {
		outbound["flow"] = cfg.Flow
	}
	if cfg.Encryption != "" {
		outbound["encryption"] = cfg.Encryption
	}
	if tlsObj != nil {
		outbound["tls"] = tlsObj
	}
	if transportObj != nil {
		outbound["transport"] = transportObj
	}

	config := map[string]any{
		"log": map[string]any{
			"level":     "warn",
			"timestamp": true,
		},
		"dns": map[string]any{
			"servers": []map[string]any{
				{"tag": "dns-remote", "address": "tls://1.1.1.1", "strategy": "prefer_ipv4"},
				{"tag": "dns-direct", "address": "https://1.0.0.1/dns-query", "detour": "direct"},
			},
			"rules": []map[string]any{
				{"outbound": "direct", "server": "dns-direct"},
				{"outbound": "block", "server": "dns-direct"},
			},
			"final": "dns-remote",
		},
		"inbounds": []map[string]any{
			{
				"type":        "socks",
				"tag":         "socks-in",
				"listen":      "127.0.0.1",
				"listen_port": socksPort,
			},
		},
		"outbounds": []map[string]any{
			outbound,
			{"type": "direct", "tag": "direct"},
			{"type": "block", "tag": "block"},
		},
		"route": map[string]any{
			"auto_detect_interface": true,
			"override_android_vpn":  true,
			"final":                 "proxy",
		},
	}

	data, _ := json.MarshalIndent(config, "", "  ")
	return string(data), nil
}

func SetTunFd(fd int64) string {
	mu.Lock()
	defer mu.Unlock()
	tunFd = fd
	hasTunFd = true
	return "ok"
}

func StartProxy(configJSON string) (ret string) {
	defer func() {
		if r := recover(); r != nil {
			ret = fmt.Sprintf("panic: %v", r)
		}
	}()

	mu.Lock()
	defer mu.Unlock()

	if instance != nil {
		return "already running"
	}

	ctx := makeContext()

	opts, err := C.UnmarshalExtendedContext[option.Options](ctx, []byte(configJSON))
	if err != nil {
		return fmt.Sprintf("parse error: %v", err)
	}

	subCtx, stop := context.WithCancel(ctx)
	inst, err := box.New(box.Options{
		Context: subCtx,
		Options: opts,
	})
	if err != nil {
		stop()
		return fmt.Sprintf("create error: %v", err)
	}

	if err := inst.Start(); err != nil {
		inst.Close()
		stop()
		return fmt.Sprintf("start error: %v", err)
	}

	instance = inst
	instCtx = subCtx
	instStop = stop

	statsWg.Add(1)
	go pollStats(subCtx)

	return "ok"
}

func StopProxy() string {
	mu.Lock()
	defer mu.Unlock()

	if instance == nil {
		return "not running"
	}

	if instStop != nil {
		instStop()
	}
	instance.Close()
	statsWg.Wait()
	instance = nil
	instCtx = nil
	instStop = nil
	hasTunFd = false
	return "stopped"
}

func IsRunning() bool {
	mu.Lock()
	defer mu.Unlock()
	return instance != nil
}

func GetRxBytes() int64 { return rxTotal }
func GetTxBytes() int64 { return txTotal }

func pollStats(ctx context.Context) {
	defer statsWg.Done()
	ticker := time.NewTicker(2 * time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
		}
	}
}

func makeContext() context.Context {
	inboundRegistry := inbound.NewRegistry()
	socks.RegisterInbound(inboundRegistry)
	direct.RegisterInbound(inboundRegistry)
	tun.RegisterInbound(inboundRegistry)

	outboundRegistry := outbound.NewRegistry()
	vless.RegisterOutbound(outboundRegistry)
	direct.RegisterOutbound(outboundRegistry)

	endpointRegistry := endpoint.NewRegistry()
	dnsTransportRegistry := include.DNSTransportRegistry()
	serviceRegistry := boxService.NewRegistry()

	return box.Context(context.Background(), inboundRegistry, outboundRegistry, endpointRegistry, dnsTransportRegistry, serviceRegistry)
}
