import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { getConversations, getMessages } from '../lib/api'

export const PAGE_SIZE = 20

export interface DateRange {
  dateStart?: string
  dateEnd?: string
}

export function useConversations(userId: string, range: DateRange) {
  return useInfiniteQuery({
    queryKey: ['conversations', userId, range],
    queryFn: ({ pageParam }) => getConversations({ userId, pageNum: pageParam, pageSize: PAGE_SIZE, ...range }),
    initialPageParam: 1,
    getNextPageParam: (last) => (last.pageNo < last.pages ? last.pageNo + 1 : undefined),
  })
}

export function useMessages(conversationId: string) {
  return useQuery({
    queryKey: ['messages', conversationId],
    queryFn: ({ signal }) => getMessages(conversationId, signal),
    // The chat page appends new turns locally, so it must not be replaced by a background refetch
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  })
}
