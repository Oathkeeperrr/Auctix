import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { AnalyticsSlice, DailyPoint } from '../../types';

/** Chart palette. Ordered so neighbouring wedges stay distinguishable. */
export const PALETTE = [
  '#6366F1',
  '#22D3A6',
  '#F59E0B',
  '#F472B6',
  '#38BDF8',
  '#A78BFA',
  '#FB7185',
  '#4ADE80',
];

const AXIS = '#64748B';
const GRID = 'rgba(148,163,184,0.14)';

const TOOLTIP = {
  contentStyle: {
    background: '#0B1220',
    border: '1px solid rgba(148,163,184,0.2)',
    borderRadius: 10,
    fontSize: 12,
    color: '#E2E8F0',
  },
  labelStyle: { color: '#94A3B8', fontSize: 11 },
  cursor: { stroke: 'rgba(148,163,184,0.25)' },
};

/** yyyy-MM-dd to a short axis label; the full date stays in the tooltip. */
function shortDate(iso: string) {
  const [, month, day] = iso.split('-');
  return `${day}/${month}`;
}

export function DailySeries({
  data,
  label,
  color = PALETTE[0],
}: {
  data: DailyPoint[];
  label: string;
  color?: string;
}) {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <LineChart data={data} margin={{ top: 4, right: 8, bottom: 0, left: -18 }}>
        <CartesianGrid stroke={GRID} vertical={false} />
        <XAxis
          dataKey="date"
          tickFormatter={shortDate}
          stroke={AXIS}
          tick={{ fontSize: 10 }}
          tickLine={false}
          minTickGap={24}
        />
        <YAxis stroke={AXIS} tick={{ fontSize: 10 }} tickLine={false} allowDecimals={false} width={44} />
        <Tooltip {...TOOLTIP} formatter={(value: number) => [value, label]} />
        <Line
          type="monotone"
          dataKey="count"
          name={label}
          stroke={color}
          strokeWidth={2}
          dot={false}
          activeDot={{ r: 4 }}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}

export function Distribution({ data, label }: { data: AnalyticsSlice[]; label: string }) {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={data} layout="vertical" margin={{ top: 4, right: 16, bottom: 0, left: 8 }}>
        <CartesianGrid stroke={GRID} horizontal={false} />
        <XAxis type="number" stroke={AXIS} tick={{ fontSize: 10 }} tickLine={false} allowDecimals={false} />
        <YAxis
          type="category"
          dataKey="label"
          stroke={AXIS}
          tick={{ fontSize: 10 }}
          tickLine={false}
          width={116}
        />
        <Tooltip {...TOOLTIP} cursor={{ fill: 'rgba(148,163,184,0.08)' }} formatter={(v: number) => [v, label]} />
        <Bar dataKey="count" name={label} radius={[0, 5, 5, 0]} maxBarSize={22}>
          {data.map((slice, index) => (
            <Cell key={slice.key} fill={PALETTE[index % PALETTE.length]} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

export function StatusDonut({ data }: { data: AnalyticsSlice[] }) {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <PieChart>
        <Pie
          data={data}
          dataKey="count"
          nameKey="label"
          innerRadius="52%"
          outerRadius="78%"
          paddingAngle={2}
          stroke="none"
        >
          {data.map((slice, index) => (
            <Cell key={slice.key} fill={PALETTE[index % PALETTE.length]} />
          ))}
        </Pie>
        <Tooltip {...TOOLTIP} cursor={false} />
        <Legend
          verticalAlign="bottom"
          height={28}
          iconType="circle"
          iconSize={8}
          formatter={(value: string) => <span style={{ color: '#94A3B8', fontSize: 11 }}>{value}</span>}
        />
      </PieChart>
    </ResponsiveContainer>
  );
}

/** Price trend for one lot, drawn from its stored bids. */
export function PriceTrend({ points }: { points: { at: string; amount: number }[] }) {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <LineChart data={points} margin={{ top: 4, right: 10, bottom: 0, left: -10 }}>
        <CartesianGrid stroke={GRID} vertical={false} />
        <XAxis dataKey="at" stroke={AXIS} tick={{ fontSize: 10 }} tickLine={false} minTickGap={28} />
        <YAxis
          stroke={AXIS}
          tick={{ fontSize: 10 }}
          tickLine={false}
          width={62}
          tickFormatter={(v: number) => `$${Math.round(v).toLocaleString()}`}
        />
        <Tooltip
          {...TOOLTIP}
          formatter={(value: number) => [`$${value.toLocaleString()}`, 'Bid']}
        />
        <Line
          type="stepAfter"
          dataKey="amount"
          name="Bid"
          stroke={PALETTE[1]}
          strokeWidth={2}
          dot={{ r: 2.5, fill: PALETTE[1] }}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
