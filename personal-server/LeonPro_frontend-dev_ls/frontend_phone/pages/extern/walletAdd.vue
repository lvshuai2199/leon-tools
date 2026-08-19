<template>
	<view>
		<uni-section title="参与人员" type="line">
			<view class="uni-px-5 uni-pb-5">
				<uni-data-select v-model="multipleValue" multiple wrap :localdata="range" @change="changeMultiple"
					label="换行显示"></uni-data-select>
			</view>
		</uni-section>
		
		<uni-data-select multiple v-model="value" :localdata="range" @change="change"></uni-data-select>

	</view>
</template>

<script>
	export default {
		data() {
			return {
				multipleValue: [1,],
				range: [{
					"value": 0,
					"text": "篮球运动",
					"disable": true
				}, {
					"value": 1,
					"text": "足球运动",
				}, {
					"value": 2,
					"text": "游泳健身运动"
				}, {
					"value": 3,
					"text": "跑步有氧运动",
				}, {
					"value": 4,
					"text": "网球运动"
				}, {
					"value": 5,
					"text": "羽毛球运动"
				}, {
					"value": 6,
					"text": "乒乓球运动"
				}, {
					"value": 7,
					"text": "保龄球运动"
				}]

			}
		},
		methods: {
	change(e) {
					console.log('单选值:', e);
					console.log("range", this.range)
					console.log("value", this.value)
				},
				changeMultiple(e) {
					console.log('多选值:', e);
				},
				open() {
					console.log("打开选择框")
				},
				close() {
					console.log("关闭选择框")
				},
				clear() {
					console.log("清除选择框")
				},
				// 支付宝小程序上需要这样
				sliceArray(ls) {
					if (Array.isArray(ls)) {
						return ls.slice(0, 2)
					}
					return ls
				}
		}
	}
</script>

<style>

</style>