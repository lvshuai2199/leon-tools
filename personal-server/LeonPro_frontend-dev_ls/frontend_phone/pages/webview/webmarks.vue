<template>
	<view class="container">
		<!-- 我的笔记模块 -->
		<view class="notes-module">
			<text class="module-title">网页收藏</text>
			<!-- 跳转到 WebView 页面 -->
			<navigator :url="'/pages/webview/webview?url=' + encodeURIComponent('http://124.70.134.114:8090')" hover-class="navigator-hover">
				<view class="note-box">我的笔记</view>
			</navigator>
			<navigator style="margin-top: 10px;" :url="'/pages/webview/webview?url=' + encodeURIComponent('http://wap.xhytd.com/141/141241/all.html')" hover-class="navigator-hover">
				<view class="note-box">注册码生成</view>
			</navigator>
		</view>
	</view>
</template>

<script>
	export default {
		data() {
			return {}
		},
		methods: {}
	}
</script>

<style>
	.container {
		padding: 20px; /* 容器内边距 */
		background-color: #f9f9f9; /* 背景颜色 */
	}

	.notes-module {
		margin-top: 20px; /* 与其他内容保持间隔 */
		padding: 15px;
		border: 1px solid #ccc; /* 边框 */
		border-radius: 8px; /* 圆角 */
		background-color: #ffffff; /* 背景色 */
		box-shadow: 0 2px 5px rgba(0, 0, 0, 0.1); /* 阴影效果 */
	}

	.module-title {
		font-size: 18px; /* 模块标题的字体大小 */
		font-weight: bold; /* 加粗 */
		margin-bottom: 12px; /* 模块标题与内容的间距 */
		color: #333; /* 文字颜色 */
	}

	.note-box {
		width: 100%; /* 矩形方块的宽度 */
		height: 50px; /* 矩形方块的高度 */
		background-color: #e0e0e0; /* 矩形方块的背景色 */
		border-radius: 8px; /* 矩形方块的圆角 */
		display: flex; /* 使用 flexbox 居中内容 */
		justify-content: center; /* 水平居中 */
		align-items: center; /* 垂直居中 */
		font-size: 16px; /* 字体大小 */
		color: #333; /* 字体颜色 */
		text-align: center; /* 文字居中 */
		cursor: pointer; /* 鼠标悬停时显示手指 */
		transition: background-color 0.3s; /* 动画效果 */
	}

	.note-box:hover {
		background-color: #d0d0d0; /* 悬停时的背景色 */
	}
</style>
