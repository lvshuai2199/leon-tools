<script setup lang="ts">
defineOptions({ name: 'TreeNodes' })

interface Node {
  type: 'dir' | 'file'
  name: string
  path: string
  title?: string
  children?: Node[]
}

defineProps<{
  nodes: Node[]
  active: string
  openDirs: Set<string>
}>()

defineEmits<{ open: [path: string]; toggle: [path: string] }>()
</script>

<template>
  <ul class="tree">
    <li v-for="n in nodes" :key="n.path">
      <button v-if="n.type === 'dir'" type="button" class="tree__dir" @click="$emit('toggle', n.path)">
        <span class="tree__mark">{{ openDirs.has(n.path) ? '▾' : '▸' }}</span>
        {{ n.name }}
      </button>
      <button
        v-else
        type="button"
        class="tree__file"
        :class="{ 'is-active': n.path === active }"
        @click="$emit('open', n.path)"
      >
        {{ n.title || n.name }}
      </button>
      <TreeNodes
        v-if="n.type === 'dir' && n.children && openDirs.has(n.path)"
        :nodes="n.children"
        :active="active"
        :open-dirs="openDirs"
        @open="$emit('open', $event)"
        @toggle="$emit('toggle', $event)"
      />
    </li>
  </ul>
</template>

<style scoped lang="scss">
.tree {
  margin: 0;
  padding: 0;
  list-style: none;
  .tree {
    padding-left: 14px;
  }
}
.tree__dir,
.tree__file {
  display: block;
  width: 100%;
  margin: 0;
  padding: 7px 8px;
  border: none;
  border-radius: lp.$radius-base;
  background: transparent;
  color: var(--el-text-color-primary);
  text-align: left;
  cursor: pointer;
  &:hover { background: var(--el-fill-color-light); }
}
.tree__dir { font-weight: 600; }
.tree__file.is-active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.tree__mark {
  display: inline-block;
  width: 1em;
  color: var(--el-text-color-secondary);
}
</style>
