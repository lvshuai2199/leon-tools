<template>
	<view class="page">
		<view class="hero">
			<view class="hero-main">
				<text class="hello">你好，{{ displayName }}</text>
				<text class="desc">为客户生成对应注册码</text>
			</view>
			<view class="logout" @click="handleLogout">退出</view>
		</view>

		<view class="card">
			<view v-if="quota" class="quota">
				<text class="quota-label">剩余次数</text>
				<text class="quota-value">{{ quotaText }}</text>
			</view>

			<view v-if="loadingConfigs" class="empty">
				<text>正在加载可用配置...</text>
			</view>
			<view v-else-if="companies.length === 0" class="empty">
				<text>暂无可用注册码，请联系管理员分配</text>
			</view>
			<view v-else class="form">
				<view class="field">
					<text class="label">公司</text>
					<picker :range="companies" :value="companyIndex" @change="onCompanyChange">
						<view class="picker">{{ companyName || "选择公司" }}</view>
					</picker>
				</view>
				<view class="field">
					<text class="label">组件名称</text>
					<picker :range="configNames" :value="configIndex" @change="onConfigChange">
						<view class="picker">{{ currentConfigName || "选择名称" }}</view>
					</picker>
				</view>
				<view class="field">
					<text class="label">注册码</text>
					<input
						v-model="regCode"
						class="input"
						maxlength="6"
						placeholder="输入 6 位注册码"
						@confirm="handleGenerate"
					/>
				</view>

				<view
					v-for="field in allFields"
					:key="field"
					class="result-row"
				>
					<view class="result-meta">
						<text class="result-label">{{ validityLabels[field] }}</text>
						<text class="result-value">{{ result[field] || "-" }}</text>
					</view>
					<text
						v-if="isGenerated && result[field]"
						class="copy"
						@click="copyText(result[field])"
					>
						复制
					</text>
				</view>

				<view class="actions">
					<button class="btn ghost" @click="resetAll">重置</button>
					<button class="btn primary" :loading="generating" @click="handleGenerate">生成</button>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { getUserInfo, clearUserInfo } from "@/utils/auth.js";
	import api from "@/apiUtils/index.js";

	const ALL_FIELDS = [
		"oneMonthValid",
		"twoMonthValid",
		"fourMonthValid",
		"sixMonthValid",
		"thirteenMonthValid",
		"longTimeValid",
	];
	const VALIDITY_LABELS = {
		oneMonthValid: "一个月",
		twoMonthValid: "两个月",
		fourMonthValid: "四个月",
		sixMonthValid: "六个月",
		thirteenMonthValid: "十三个月",
		longTimeValid: "永久",
	};

	function emptyResult() {
		return {
			oneMonthValid: "OneMonth",
			twoMonthValid: "TwoMonth",
			fourMonthValid: "FourMonth",
			sixMonthValid: "SixMonth",
			thirteenMonthValid: "ThirteenMonth",
			longTimeValid: "Forever",
		};
	}

	export default {
		data() {
			return {
				user: null,
				quota: null,
				validityLabels: VALIDITY_LABELS,
				configs: [],
				loadingConfigs: false,
				companyName: "",
				configId: "",
				regCode: "",
				generating: false,
				isGenerated: false,
				result: emptyResult(),
			};
		},
		computed: {
			api() {
				return this.$api || api;
			},
			displayName() {
				return this.user?.nickname || this.user?.username || "用户";
			},
			quotaText() {
				if (!this.quota) return "-";
				if (this.quota.unlimited) return "不限";
				const remaining = this.quota.remaining ?? 0;
				const limit = this.quota.generateLimit ?? 0;
				return remaining + " / " + limit;
			},
			companies() {
				const names = [];
				this.configs.forEach((item) => {
					if (item.company && !names.includes(item.company)) {
						names.push(item.company);
					}
				});
				return names;
			},
			companyIndex() {
				return Math.max(this.companies.indexOf(this.companyName), 0);
			},
			currentConfigs() {
				return this.configs.filter((item) => item.company === this.companyName);
			},
			configNames() {
				return this.currentConfigs.map((item) => item.name || item.componentName || item.id);
			},
			configIndex() {
				return Math.max(
					this.currentConfigs.findIndex((item) => item.id === this.configId),
					0
				);
			},
			currentConfig() {
				return (
					this.currentConfigs.find((item) => item.id === this.configId) ||
					this.currentConfigs[0] ||
					null
				);
			},
			currentConfigName() {
				return this.currentConfig?.name || this.currentConfig?.componentName || "";
			},
			allFields() {
				return ALL_FIELDS;
			},
		},
		onLoad() {
			this.ensureLogin();
		},
		onShow() {
			this.ensureLogin();
		},
		onPullDownRefresh() {
			Promise.all([this.loadConfigs(), this.loadQuota()]).finally(() => uni.stopPullDownRefresh());
		},
		methods: {
			ensureLogin() {
				const user = getUserInfo();
				if (!user) {
					uni.reLaunch({ url: "/pages/login/login" });
					return;
				}
				this.user = user;
				this.loadConfigs();
				this.loadQuota();
			},
			applyDefaultSelection() {
				const list = Array.isArray(this.configs) ? this.configs : [];
				const company = this.companies[0] || list[0]?.company || "";
				this.companyName = company;
				const first = list.find((item) => item.company === company) || list[0];
				this.configId = first?.id || "";
			},
			normalizeConfigs(data) {
				if (Array.isArray(data)) {
					return data;
				}
				if (data && Array.isArray(data.records)) {
					return data.records;
				}
				return [];
			},
			onCompanyChange(event) {
				this.companyName = this.companies[event.detail.value] || "";
				this.configId = this.currentConfigs[0]?.id || "";
				this.resetResult();
			},
			onConfigChange(event) {
				this.configId = this.currentConfigs[event.detail.value]?.id || "";
				this.resetResult();
			},
			resetResult() {
				this.isGenerated = false;
				this.result = emptyResult();
			},
			resetAll() {
				this.regCode = "";
				this.resetResult();
			},
			async loadQuota() {
				try {
					this.quota = (await this.api.myQuota()) || null;
				} catch (error) {
					console.error(error);
				}
			},
			async loadConfigs() {
				this.loadingConfigs = true;
				try {
					this.configs = this.normalizeConfigs(await this.api.listRegCodeConfig());
					this.applyDefaultSelection();
					this.$nextTick(() => this.applyDefaultSelection());
				} catch (error) {
					console.error(error);
					this.configs = [];
				} finally {
					this.loadingConfigs = false;
				}
			},
			async handleGenerate() {
				if (!this.currentConfig?.id) {
					uni.showToast({ title: "请选择名称", icon: "none" });
					return;
				}
				if (!this.regCode || this.regCode.length !== 6) {
					uni.showToast({ title: "注册码长度必须为 6 位", icon: "none" });
					return;
				}
				if (this.quota && !this.quota.unlimited && (this.quota.remaining || 0) <= 0) {
					uni.showToast({ title: "生成次数已用完", icon: "none" });
					return;
				}
				this.generating = true;
				try {
					const data = await this.api.genTempRegCode({
						regCode: this.regCode,
						configId: this.currentConfig.id,
						company: this.currentConfig.company,
						applyName: this.currentConfig.name,
						applyId: this.user?.id,
					});
					this.result = { ...emptyResult(), ...(data || {}) };
					this.isGenerated = true;
					uni.showToast({ title: "生成成功", icon: "success" });
					this.loadQuota();
				} catch (error) {
					console.error(error);
				} finally {
					this.generating = false;
				}
			},
			copyText(text) {
				uni.setClipboardData({
					data: String(text),
					success: () => uni.showToast({ title: "已复制", icon: "success" }),
				});
			},
			handleLogout() {
				uni.showModal({
					title: "退出登录",
					content: "确定退出当前账号？",
					success: (res) => {
						if (!res.confirm) return;
						this.user = null;
						clearUserInfo();
						this.goLogin();
					},
				});
			},
			goLogin() {
				uni.reLaunch({
					url: "/pages/login/login",
					fail: () => {
						uni.redirectTo({
							url: "/pages/login/login",
							fail: () => {
								// #ifdef H5
								window.location.replace((window.location.origin || "") + "/h5/");
								// #endif
							},
						});
					},
				});
			},
		},
	};
</script>

<style scoped>
	.page {
		min-height: 100vh;
		padding: 24rpx 24rpx 48rpx;
		box-sizing: border-box;
		background: #f4f6fb;
	}

	.hero {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 8rpx 8rpx 24rpx;
	}

	.hello {
		display: block;
		font-size: 36rpx;
		font-weight: 700;
		color: #111827;
	}

	.desc {
		display: block;
		margin-top: 8rpx;
		font-size: 24rpx;
		color: #6b7280;
	}

	.logout {
		padding: 12rpx 22rpx;
		font-size: 24rpx;
		color: #4080ff;
		background: #e8f0ff;
		border-radius: 999rpx;
	}

	.card {
		padding: 28rpx;
		background: #fff;
		border-radius: 20rpx;
	}

	.quota {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 20rpx 24rpx;
		margin-bottom: 24rpx;
		background: #fff7ed;
		border-radius: 14rpx;
	}

	.quota-label {
		font-size: 24rpx;
		color: #9a3412;
	}

	.quota-value {
		font-size: 30rpx;
		font-weight: 600;
		color: #c2410c;
	}

	.field {
		margin-bottom: 24rpx;
	}

	.label {
		display: block;
		margin-bottom: 10rpx;
		font-size: 24rpx;
		color: #6b7280;
	}

	.input,
	.picker {
		height: 84rpx;
		padding: 0 24rpx;
		line-height: 84rpx;
		font-size: 28rpx;
		background: #f5f7fb;
		border-radius: 14rpx;
	}

	.result-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 18rpx 0;
		border-bottom: 1px solid #f3f4f6;
	}

	.result-label {
		display: block;
		font-size: 22rpx;
		color: #9ca3af;
	}

	.result-value {
		display: block;
		margin-top: 6rpx;
		font-size: 28rpx;
		color: #4080ff;
		word-break: break-all;
	}

	.copy,
	.link {
		font-size: 26rpx;
		color: #4080ff;
	}

	.link {
		margin: 16rpx 0;
		text-align: center;
	}

	.actions {
		display: flex;
		margin-top: 12rpx;
	}

	.actions .btn + .btn {
		margin-left: 16rpx;
	}

	.btn {
		flex: 1;
		height: 84rpx;
		line-height: 84rpx;
		font-size: 28rpx;
		border-radius: 14rpx;
		border: none;
	}

	.btn::after {
		border: none;
	}

	.btn.primary {
		color: #fff;
		background: #4080ff;
	}

	.btn.ghost {
		color: #374151;
		background: #f3f4f6;
	}

	.empty {
		text-align: center;
		color: #9ca3af;
		font-size: 26rpx;
	}
</style>
