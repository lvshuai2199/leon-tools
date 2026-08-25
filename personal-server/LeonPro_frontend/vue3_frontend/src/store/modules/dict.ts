import { store } from "@/store";
import DictionaryAPI, { type DictVO, type DictData } from "@/api/system/dict";

export const useDictStore = defineStore("dict", () => {
  const dictionary = useStorage<Record<string, DictData[]>>("dictionary", {});

  const setDictionary = (dict: DictVO) => {
    dictionary.value[dict.dictCode] = dict.dictDataList;
  };

  /**
   * 加载字典数据
   *
   * LeonPro_backend 暂未提供字典接口，
   * 加载失败时静默跳过，不影响登录流程
   */
  const loadDictionaries = async () => {
    try {
      const dictList = await DictionaryAPI.getList();
      if (Array.isArray(dictList)) {
        dictList.forEach(setDictionary);
      }
    } catch (error) {
      console.warn("字典数据加载失败（后端暂未提供字典接口，已跳过）:", error);
    }
  };

  const getDictionary = (dictCode: string): DictData[] => {
    return dictionary.value[dictCode] || [];
  };

  const clearDictionaryCache = () => {
    dictionary.value = {};
  };

  const updateDictionaryCache = async () => {
    clearDictionaryCache(); // 先清除旧缓存
    await loadDictionaries(); // 重新加载最新字典数据
  };

  return {
    dictionary,
    setDictionary,
    loadDictionaries,
    getDictionary,
    clearDictionaryCache,
    updateDictionaryCache,
  };
});

export function useDictStoreHook() {
  return useDictStore(store);
}
