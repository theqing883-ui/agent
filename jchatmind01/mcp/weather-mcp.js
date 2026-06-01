import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
    CallToolRequestSchema,
    ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";

const API_KEY = process.env.HEFENG_API_KEY;
const GEO_API = "https://geoapi.qweather.com/v2/city/lookup";
const WEATHER_API = "https://devapi.qweather.com/v7/weather/now";

if (!API_KEY) {
    console.error("Missing HEFENG_API_KEY environment variable");
    process.exit(1);
}

const server = new Server(
    { name: "hefeng-weather-server", version: "1.0.0" },
    { capabilities: { tools: {} } }
);

// 工具列表
server.setRequestHandler(ListToolsRequestSchema, async () => {
    return {
        tools: [
            {
                name: "hefeng_city_lookup",
                description: "根据城市名查询和风天气 LocationID 和经纬度坐标。用于客户端定位，输入城市名称返回该城市的 ID、坐标、行政区划信息。",
                inputSchema: {
                    type: "object",
                    properties: {
                        city: { type: "string", description: "城市名称，如 北京、西安、成都" }
                    },
                    required: ["city"],
                },
            },
            {
                name: "hefeng_weather_now",
                description: "查询指定城市的实时天气。返回温度、体感温度、天气状况、风力、湿度、能见度等数据。",
                inputSchema: {
                    type: "object",
                    properties: {
                        city: { type: "string", description: "城市名称，如 北京、西安、成都" }
                    },
                    required: ["city"],
                },
            },
        ],
    };
});

// 城市查询
async function lookupCity(city) {
    const url = `${GEO_API}?location=${encodeURIComponent(city)}&key=${API_KEY}`;
    const res = await fetch(url);
    if (!res.ok) throw new Error(`City lookup API returned status: ${res.status}`);
    const data = await res.json();
    if (data.code !== "200" || !data.location || data.location.length === 0) {
        throw new Error(`未找到城市: ${city}`);
    }
    const loc = data.location[0];
    return {
        id: loc.id,
        name: loc.name,
        adm1: loc.adm1,      // 省
        adm2: loc.adm2,      // 市
        country: loc.country,
        lat: loc.lat,
        lon: loc.lon,
    };
}

// 天气查询
async function fetchWeather(locationId) {
    const url = `${WEATHER_API}?location=${locationId}&key=${API_KEY}`;
    const res = await fetch(url);
    if (!res.ok) throw new Error(`Weather API returned status: ${res.status}`);
    const data = await res.json();
    if (data.code !== "200" || !data.now) {
        throw new Error("天气数据获取失败");
    }
    return data.now;
}

// 工具调用处理
server.setRequestHandler(CallToolRequestSchema, async (request) => {
    const { name, arguments: args } = request.params;

    try {
        if (name === "hefeng_city_lookup") {
            const city = args?.city;
            if (!city) throw new Error("缺少 city 参数");

            const loc = await lookupCity(city);
            return {
                content: [{
                    type: "text",
                    text: JSON.stringify(loc, null, 2),
                }],
            };
        }

        if (name === "hefeng_weather_now") {
            const city = args?.city;
            if (!city) throw new Error("缺少 city 参数");

            const loc = await lookupCity(city);
            const weather = await fetchWeather(loc.id);

            const result = {
                location: { name: loc.name, adm1: loc.adm1, adm2: loc.adm2, lat: loc.lat, lon: loc.lon },
                weather: {
                    temp: weather.temp + "°C",
                    feelsLike: weather.feelsLike + "°C",
                    text: weather.text,
                    windDir: weather.windDir,
                    windScale: weather.windScale + "级",
                    humidity: weather.humidity + "%",
                    vis: weather.vis + "km",
                    precip: weather.precip + "mm/h",
                    pressure: weather.pressure + "hPa",
                    cloud: weather.cloud,
                },
            };

            return {
                content: [{
                    type: "text",
                    text: JSON.stringify(result, null, 2),
                }],
            };
        }

        throw new Error(`Unknown tool: ${name}`);
    } catch (error) {
        return {
            content: [{
                type: "text",
                text: `Error: ${error.message}`,
            }],
            isError: true,
        };
    }
});

const transport = new StdioServerTransport();
server.connect(transport).catch(console.error);
