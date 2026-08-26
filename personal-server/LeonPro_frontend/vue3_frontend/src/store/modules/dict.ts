import { store } from "@/store";
import { type DictVO, type DictData } from "@/api/system/dict";

export const useDictStore = defineStore("dict", () => {
  const dictionary = useStorage<Record<string, DictData[]>>("dictionary", {});

  const setDictionary = (dict: DictVO) => {
    dictionary.value[dict.dictCode] = dict.dictDataList;
  };

  /**
   * 加载字典数据
   *
   * LeonPro_backend 暂未提供字典接口（/api/v1/dict/list 会 404），
   * 直接跳过不发请求，避免登录时弹出错误提示。
   * 后端提供字典接口后，恢复 DictionaryAPI.getList() 调用即可。
   */
  const loadDictionaries = async () => {
    // const dictList = await DictionaryAPI.getList();
    // if (Array.isArray(dictList)) {
    //   dictList.forEach(setDictionary);
    // }
    return Promise.resolve();
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
